package edu.mayo.bsi.nlp2fhir.evaluation.evaluators;

import edu.mayo.bsi.nlp2fhir.evaluation.api.ResourceEvaluationTask;
import edu.mayo.bsi.nlp2fhir.performance.structs.AnnotationCache;
import edu.mayo.bsi.nlp2fhir.evaluation.SHARPNGoldStandardAnalysisEngine;
import edu.mayo.bsi.nlp2fhir.evaluation.api.ResourceEvaluationTask;
import edu.mayo.bsi.nlp2fhir.performance.structs.AnnotationCache;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.log4j.Logger;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.DomainResource;
import org.hl7.fhir.Resource;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DeepSearchResourceEvaluator<T extends Resource> implements ResourceEvaluationTask<T> {

    private List<String> evaluationPaths;
    private Set<String> valueChecks;
    /**
     * A mapping of paths to the call stacks
     */
    private Map<String, List<Method>> callStacks;
    /**
     * Path->True Positive Count
     */
    private Map<String, AtomicInteger> truePosMap;
    /**
     * Path->False Positive Count
     */
    private Map<String, AtomicInteger> falsePosMap;
    /**
     * Path->False Negative Count
     */
    private Map<String, AtomicInteger> falseNegMap;

    private Class<T> resourceClass;

    /**
     * Paths for resource identifiers used to determine whether a root resource matches in addition to a position match
     */
    private List<String> identifiers;

    /**
     * Construct a new evaluator that evaluates the given resource
     *
     * @param identifiers   Paths that are used to identify base level resource matches
     * @param resourceClass The class of the FHIR resource to evaluate
     * @param paths         A list of paths to evaluate
     * @param valueChecks   A set of paths to compare values for (as opposed to position)
     */
    public DeepSearchResourceEvaluator(Class<T> resourceClass, List<String> identifiers, List<String> paths, Set<String> valueChecks) {
        this.resourceClass = resourceClass;
        this.evaluationPaths = paths;
        this.valueChecks = valueChecks;
        this.callStacks = new HashMap<>();
        this.truePosMap = new HashMap<>();
        this.falsePosMap = new HashMap<>();
        this.falseNegMap = new HashMap<>();
        this.identifiers = identifiers;
    }

    @Override
    public Class<T> getResourceClass() {
        return resourceClass;
    }

    //TODO these evaluations short circuit after a single element matches in coll, maybe check all elements and increment/decrement counts as appropriate?
    public void evaluate(JCas goldView, JCas baseView) {
        // Create an annotation cache of the gold view
        AnnotationCache.AnnotationTree baseAnnCache = AnnotationCache.getAnnotationCache(JCasUtil.selectSingle(baseView, DocumentID.class).getDocumentID() + "_base", baseView);
        AnnotationCache.AnnotationTree goldAnnCache = AnnotationCache.getAnnotationCache(JCasUtil.selectSingle(goldView, DocumentID.class).getDocumentID() + "_gold", goldView);
        String pathRoot = getResourceClass().getSimpleName();
        // Check true/false positives on extraction
        for (T extractedResource : JCasUtil.select(baseView, getResourceClass())) {
            Collection<T> goldResources = goldAnnCache.getCollisions(extractedResource.getBegin(), extractedResource.getEnd(), getResourceClass());
//            Collection<T> goldResources = JCasUtil.selectAt(goldView, getResourceClass(), extractedResource.getBegin(), extractedResource.getEnd());
            T goldResource = null;
            if (goldResources.size() == 0) { // No match/failed to extract
                falsePosMap.computeIfAbsent(pathRoot, k -> new AtomicInteger(0)).incrementAndGet();
                for (String path : identifiers) {
                    falsePosMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                }
                continue;
            } else {
                boolean matchingResource = false;
                for (T resource : goldResources) {
                    boolean matchesAll = true;
                    goldResource = resource;
                    for (String path : identifiers) { // Has to be nested here as we need to check all as a group
                        List<Method> pathStack = callStacks.computeIfAbsent(path, this::constructCallStack);
                        if (pathStack == null) {
                            throw new AssertionError("No call stack for " + path + " despite being defined");
                        }
                        Collection<Object> extractedColl = runCallStackRecurs(extractedResource, new LinkedList<>(pathStack));
                        Collection<Object> goldColl = runCallStackRecurs(goldResource, new LinkedList<>(pathStack));
                        if (extractedColl.isEmpty() && goldColl.isEmpty()) {
                            throw new IllegalArgumentException("Cannot use " + path + " as an identifier when it is possible to be empty in gold standard");
                        }
                        if (extractedColl.isEmpty()) {
                            matchesAll = false;
                            break;
                        }
                        if (valueChecks.contains(path)) { // Value check, so a direct string comparison TODO O(n^2) find a better way to do this
                            boolean truePos = false;
                            for (Object extracted : extractedColl) {
                                for (Object gold : goldColl) {
                                    if (extracted.toString().toUpperCase().equals(gold.toString().toUpperCase())) {
                                        truePos = true;
                                        break;
                                    }
                                }
                                if (truePos) {
                                    break;
                                }
                            }
                            if (!truePos) {
                                matchesAll = false;
                                break;
                            }
                        } else { // Position check TODO O(n^2) find a better way to do this
                            boolean truePos = false;
                            for (Object extracted : extractedColl) {
                                for (Object gold : goldColl) {
                                    Annotation extractedAnn = (Annotation) extracted;
                                    Annotation goldAnn = (Annotation) gold;
                                    if (collides(extractedAnn, goldAnn)) {
                                        truePos = true;
                                        break;
                                    }
                                }
                                if (truePos) {
                                    break;
                                }
                            }
                            if (!truePos) {
                                matchesAll = false;
                                break;
                            }
                        }
                    }
                    if (matchesAll) {
                        matchingResource = true;
                        break;
                    }
                }
                if (matchingResource) {
                    truePosMap.computeIfAbsent(pathRoot, k -> new AtomicInteger(0)).incrementAndGet();
                } else {
                    falsePosMap.computeIfAbsent(pathRoot, k -> new AtomicInteger(0)).incrementAndGet();
                    for (String path : identifiers) {
                        falsePosMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                    }
                    continue; // TODO double check correctness
                }
            }
            for (String path : evaluationPaths) {
                // For each path associated with the extracted resource
                List<Method> pathStack = callStacks.computeIfAbsent(path, this::constructCallStack);
                if (pathStack == null) {
                    throw new AssertionError("No call stack for " + path + " despite being defined");
                }
                Collection<Object> extractedColl = runCallStackRecurs(extractedResource, new LinkedList<>(pathStack));
                Collection<Object> goldColl = runCallStackRecurs(goldResource, new LinkedList<>(pathStack));
                if (extractedColl.isEmpty() && goldColl.isEmpty()) {
                    continue; // Both empty, so neither a true positive or a false positive
                }
                if (goldColl.isEmpty()) { // implies extracted not empty
                    falsePosMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                    continue;
                }
                if (extractedColl.isEmpty()) { // Implies gold not empty
                    continue; // We catch false negatives in a later check
                }
                if (valueChecks.contains(path)) { // Value check, so a direct string comparison TODO O(n^2) find a better way to do this
                    boolean truePos = false;
                    for (Object extracted : extractedColl) {
                        for (Object gold : goldColl) {
                            if (extracted.toString().toUpperCase().equals(gold.toString().toUpperCase())) {
                                truePos = true;
                                break;
                            }
                        }
                        if (truePos) {
                            break;
                        }
                    }
                    if (truePos) {
                        truePosMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                    } else {
                        falsePosMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                    }
                } else { // Position check TODO O(n^2) find a better way to do this
                    boolean truePos = false;
                    for (Object extracted : extractedColl) {
                        for (Object gold : goldColl) {
                            Annotation extractedAnn = (Annotation) extracted;
                            Annotation goldAnn = (Annotation) gold;
                            if (collides(extractedAnn, goldAnn)) {
                                truePos = true;
                                break;
                            }
                        }
                        if (truePos) {
                            break;
                        }
                    }
                    if (truePos) {
                        truePosMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                    } else {
                        falsePosMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                    }
                }
            }
        }
        // Check false negatives
        for (T goldResource : JCasUtil.select(goldView, getResourceClass())) {
            Collection<T> extractedResources = baseAnnCache.getCollisions(goldResource.getBegin(), goldResource.getEnd(), getResourceClass());
//            Collection<T> extractedResources = JCasUtil.selectAt(baseView, getResourceClass(), goldResource.getBegin(), goldResource.getEnd());
            if (extractedResources.size() == 0) { // No match/failed to extract
                falseNegMap.computeIfAbsent(pathRoot, k -> new AtomicInteger(0)).incrementAndGet();
                for (String path : identifiers) {
                    falseNegMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                }
                continue; // TODO
//                for (String path : evaluationPaths) {
//                    // For each path associated with the extracted resource
//                    List<Method> pathStack = callStacks.computeIfAbsent(path, this::constructCallStack);
//                    if (pathStack == null) {
//                        throw new AssertionError("No call stack for " + path + " despite being defined");
//                    }
//                    Collection<Object> gold = runCallStackRecurs(goldResource, new LinkedList<>(pathStack));
//                    if (gold.isEmpty()) {
//                        continue; // Both empty, so neither a true positive or a false positive
//                    }
//                    falseNegMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
//                }
            } else {
                for (String path : evaluationPaths) { // For each path associated with the extracted resource
                    boolean foundMatchForGold = false;
                    for (T extractedResource : extractedResources) { // Check all matching gold standard resources for that same object
                        List<Method> pathStack = callStacks.computeIfAbsent(path, this::constructCallStack);
                        if (pathStack == null) {
                            throw new AssertionError("No call stack for " + path + " despite being defined");
                        }
                        Collection<Object> extractedColl = runCallStackRecurs(extractedResource, new LinkedList<>(pathStack));
                        Collection<Object> goldColl = runCallStackRecurs(goldResource, new LinkedList<>(pathStack));
                        if (extractedColl.isEmpty() && goldColl.isEmpty()) {
                            foundMatchForGold = true; // Both empty, so it matches
                            continue; // Both empty, so ignore
                        }
                        if (valueChecks.contains(path)) { // Value check, so a direct string comparison TODO O(n^2) find a better way to do this
                            for (Object gold : goldColl) {
                                for (Object extracted : extractedColl) {
                                    if (extracted.toString().toUpperCase().equals(gold.toString().toUpperCase())) {
                                        foundMatchForGold = true;
                                        break;
                                    }
                                }
                                if (foundMatchForGold) {
                                    break;
                                }
                            }
                        } else { // Position check TODO O(n^2) find a better way to do this
                            for (Object gold : goldColl) {
                                for (Object extracted : extractedColl) {
                                    Annotation extractedAnn = (Annotation) extracted;
                                    Annotation goldAnn = (Annotation) gold;
                                    if (collides(extractedAnn, goldAnn)) {
                                        foundMatchForGold = true;
                                        break;
                                    }
                                }
                                if (foundMatchForGold) {
                                    break;
                                }
                            }
                        }
                    }
                    if (!foundMatchForGold) {
                        falseNegMap.computeIfAbsent(path, k -> new AtomicInteger(0)).incrementAndGet();
                    }
                }
            }
            // We aren't interested in true positives because we already processed them earlier
        }
    }

    @Override
    public List<String> generateResults() {
        evaluationPaths.sort(Comparator.naturalOrder());
        List<String> ret = new LinkedList<>();
        for (String path : evaluationPaths) {
            int truePos = truePosMap.getOrDefault(path, new AtomicInteger(0)).get();
            int falsePos = falsePosMap.getOrDefault(path, new AtomicInteger(0)).get();
            int falseNeg = falseNegMap.getOrDefault(path, new AtomicInteger(0)).get();
            double precision = ((double) truePos) / (truePos + falsePos);
            double recall = ((double) truePos) / (truePos + falseNeg);
            double f1 = 2 * (precision * recall) / (precision + recall);
            ret.add(path + "\t" + precision + "\t" + recall + "\t" + f1 + "\t" + truePos + "\t" + falsePos + "\t" + falseNeg);
        }
        return ret;
    }

    private List<Method> constructCallStack(String path) {
        LinkedList<Method> ret = new LinkedList<>();
        String[] pathArr = path.split("\\.");
        Class<?> currClass = getResourceClass();
        for (int i = 1; i < pathArr.length; i++) { // Skip over root path element since we already have that object
            try {
                String methodName = "get" + pathArr[i].substring(0, 1).toUpperCase() + (pathArr[i].length() > 1 ? pathArr[i].substring(1) : "");
                Method m = currClass.getDeclaredMethod(methodName);
                m.setAccessible(true);
                if (FSArray.class.isAssignableFrom(m.getReturnType())) {
                    currClass = currClass.getDeclaredMethod(methodName, int.class).getReturnType();
                } else {
                    currClass = m.getReturnType();
                }
                ret.addLast(m);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e); //We assert that this was validated during configuration
            }
        }
        return ret;
    }

    private Collection<Object> runCallStackRecurs(Object source, LinkedList<Method> stack) {
        if (source == null) {
            return new LinkedList<>();
        }
        if (stack.size() == 0) {
            if (source instanceof FSArray) {
                return Arrays.asList(((FSArray) source).toArray());
            } else {
                return Collections.singleton(source);
            }
        }
        Method getter = stack.removeFirst();
        if (source instanceof FSArray) {
            Collection<Object> ret = new LinkedList<>();
            for (FeatureStructure fs : ((FSArray) source).toArray()) {
                LinkedList<Method> runStack = new LinkedList<>(stack); // Make a copy because each branch will be popping off the stack
                try {
                    ret.addAll(runCallStackRecurs(getter.invoke(fs), runStack));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException();
                }
            }
            return ret;
        } else {
            try {
                return runCallStackRecurs(getter.invoke(source), stack);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean collides(Annotation ann1, Annotation ann2) {
        return ann1.getBegin() <= ann2.getBegin() && ann1.getEnd() > ann2.getBegin() || (ann1.getBegin() >= ann2.getBegin())
                && ann1.getBegin() <= ann2.getEnd();
    }
}

