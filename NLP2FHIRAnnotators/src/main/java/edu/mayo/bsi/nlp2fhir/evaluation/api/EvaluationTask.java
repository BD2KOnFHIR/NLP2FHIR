package edu.mayo.bsi.nlp2fhir.evaluation.api;

import edu.mayo.bsi.nlp2fhir.performance.structs.AnnotationCache;
import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.GoldStandardEvaluationAnalysisEngine;
import edu.mayo.bsi.nlp2fhir.performance.structs.AnnotationCache;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Represents an evaluation task for matching extracted items with gold standard
 * @param <ANN_TYPE> The FHIR type to check against
 */
public abstract class EvaluationTask<ANN_TYPE extends Annotation> {

    private final String type;
    private final File evalDir;
    private static final File EVAL_DEBUG = new File("eval_debug.txt");
    private static BufferedWriter DEBUG_WRITER;

    static {
        try {
            DEBUG_WRITER = new BufferedWriter(new FileWriter(EVAL_DEBUG));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param type The knowtator type to check against
     * @param evalDir The temporary evaluation directory
     */
    public EvaluationTask(String type, File evalDir) {
        this.type = type;
        this.evalDir = evalDir;
    }

    public void evaluate(String docID, List<GoldStandardEvaluationAnalysisEngine.Segment> ranges, Map<String, Collection<KnowtatorAnnotation>> typeToAnns, AnnotationCache.AnnotationTree annCache) {
        // Set up variables
        int truePositiveNLP2FHIR = 0;
        int falsePositiveNLP2FHIR = 0;
        int falseNegativeNLP2FHIR = 0;
        // Check NLP2FHIR Matches (True Positive, False Positive)
        for (ANN_TYPE t : getInterestedAnnotations(ranges, annCache, getAnnotationClass())) {
            if (hasInterestedProperty(t)) { // - Has property of interest
                boolean flag = false;
                // - Check all colliding gold standard annotations
                for (KnowtatorAnnotation ann : annCache.getCollisions(t.getBegin(), t.getEnd(), KnowtatorAnnotation.class)) {
                    if (ann.getClassdef() == null) { // ??? Irregularities in gold standard annotation, skip.
                        System.out.println("Null classdef in annotation " + ann.getBegin() + ":" + ann.getEnd() + " in " + docID);
                    } else if (ann.getClassdef().equalsIgnoreCase(type)) { // Same type
                        if (matches(t, ann)) {
                            flag = true;
                            truePositiveNLP2FHIR++;
                            break;
                        }
                    }
                }
                if (!flag) {
                    try {
                        DEBUG_WRITER.append("False Positive Found: " + type + (getSuffix() == null ? "" : "_" + getSuffix()) + " in " + docID + " with value " + t.getCoveredText() + "\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    falsePositiveNLP2FHIR++;
                }
            }
        }
        // Check Knowtator Matches (False Negative)
        for (KnowtatorAnnotation ann : typeToAnns.getOrDefault(type, new LinkedList<>())) {
            boolean flag = false;
            // - Check all colliding normal annotations
            for (ANN_TYPE t : annCache.getCollisions(ann.getBegin(), ann.getEnd(), getAnnotationClass())) {
                if (matches(t, ann)) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                falseNegativeNLP2FHIR++;
                try {
                    DEBUG_WRITER.append("False Negative Found: " + type + (getSuffix() == null ? "" : "_" + getSuffix()) + " in " + docID + " with value " + ann.getStandardText() + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // -- Write out results
        File out = new File(evalDir, docID + "-_-_-" + type + (getSuffix() == null ? "" : "_" + getSuffix()) + ".eval");
        FileWriter writer = null;
        try {
            writer = new FileWriter(out);
            writer.write(truePositiveNLP2FHIR + "|" + falsePositiveNLP2FHIR + "|" + falseNegativeNLP2FHIR);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            DEBUG_WRITER.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return null by default, used when a suffix is necessary for evaluation (i.e. when gold standard type name is same across
     * multiple elements)
     */
    protected String getSuffix() {
        return null;
    }

    /**
     * Evaluates whether the annotation in the first parameter matches the gold standard annotation
     * @param ann The annotation to evaluate
     * @param gold The gold standard annotation of type {@link #type} to match against
     * @return True if match (should be counted as a true positive), false if not
     */
    protected abstract boolean matches(ANN_TYPE ann, KnowtatorAnnotation gold);

    /**
     * @param ann The annotation to check
     * @return Whether the given annotation has the type/relevant fields we are looking for (i.e. whether it should be
     * evaluated for false/true positive
     */
    protected abstract boolean hasInterestedProperty(ANN_TYPE ann);

    public abstract Class<ANN_TYPE> getAnnotationClass();

    private <T extends Annotation> Collection<T> getInterestedAnnotations(List<GoldStandardEvaluationAnalysisEngine.Segment> segments, AnnotationCache.AnnotationTree annCache, Class<T> clazz) {
        Collection<T> ret = new LinkedHashSet<>(); // Preserve ordering
        for (GoldStandardEvaluationAnalysisEngine.Segment s : segments) {
            ret.addAll(annCache.getCollisions(s.start, s.end, clazz));
        }
        return ret;
    }
}
