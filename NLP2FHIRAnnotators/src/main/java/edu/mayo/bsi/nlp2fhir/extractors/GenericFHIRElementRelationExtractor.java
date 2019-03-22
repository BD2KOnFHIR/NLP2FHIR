package edu.mayo.bsi.nlp2fhir.extractors;

import edu.mayo.bsi.nlp2fhir.extractors.features.*;
import edu.mayo.bsi.nlp2fhir.nlp.GenericRelation;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.Feature;
import org.cleartk.ml.Instance;
import org.cleartk.ml.jar.JarClassifierBuilder;

import java.io.File;
import java.util.*;

public class GenericFHIRElementRelationExtractor extends CleartkAnnotator<Boolean> {

    private Class<? extends Annotation> baseClass;
    private Class<? extends Annotation> associationClazz;
    public static final String RELATION_NAME_PARAM = "REL_NAME";
    @ConfigurationParameter(
            name = "REL_NAME",
            description = "The name of this relation"
    )
    private String relationType;
    public static final String RELATION_ARG1_CLASS_PARAM = "ARG1";
    @ConfigurationParameter(
            name = "ARG1",
            description = "The class of the first argument in the relation"
    )
    private String arg1clazz;
    public static final String RELATION_ARG2_CLASS_PARAM = "ARG2";
    @ConfigurationParameter(
            name = "ARG2",
            description = "The class of the second argument in the relation"
    )
    private String arg2clazz;

    private int posInstances;
    private int negInstances;

    @SuppressWarnings("unchecked")
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        try {
            baseClass = (Class<? extends Annotation>) Class.forName(arg1clazz);
            associationClazz = (Class<? extends Annotation>) Class.forName(arg2clazz);
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new ResourceInitializationException(e);
        }
        posInstances = 0;
        negInstances = 0;

    }

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
        Map<Annotation, Set<Annotation>> rels = new HashMap<>(); // Used only for training
        if (isTraining()) {
            for (GenericRelation rel : JCasUtil.select(cas, GenericRelation.class)) {
                if (!rel.getRelationType().equalsIgnoreCase(relationType.toUpperCase())) {
                    continue;
                }
                Annotation ann1 = rel.getArg1();
                Annotation ann2 = rel.getArg2();
                rels.computeIfAbsent(ann1, k -> new HashSet<>()).add(ann2);
            }
        }

        for (Annotation ann1 : JCasUtil.select(cas, baseClass)) {
            for (Annotation ann2 : JCasUtil.select(cas, associationClazz)) {
                List<Feature> features = new LinkedList<>();
                boolean continueExec = true;
                for (FeatureExtractor<Annotation, Annotation> extractor : getFeatureExtractors()) {
                    try {
                        features.addAll(extractor.extract(cas, ann1, ann2));
                    } catch (Exception e) {
                        continueExec = false; // TODO log
                    }
                }
                if (!continueExec) {
                    continue;
                }
                if (isTraining()) {
                    boolean isRelation = rels.getOrDefault(ann1, new HashSet<>()).contains(ann2);
                    this.dataWriter.write(new Instance<>(isRelation, features));
                    if (isRelation) {
                        posInstances++;
                    } else {
                        negInstances++;
                    }
                } else {
                    if (this.classifier.classify(features)) {
                        GenericRelation rel = new GenericRelation(cas);
                        rel.setArg1(ann1);
                        rel.setArg2(ann2);
                        rel.setRelationType(relationType.toUpperCase());
                        rel.addToIndexes();
                    }
                }
            }
        }
    }

    private List<FeatureExtractor<Annotation, Annotation>> getFeatureExtractors() {
        return Arrays.asList(
                new TokenFeatureExtractor(),
                new PartOfSpeechFeatureExtractor(),
                new PhraseChunkingFeatureExtractor(),
                new CTakesNamedEntityFeatureExtractor(),
                new DependencyTreeFeatureExtractor(),
                new DependencyPathFeatureExtractor()
        );
    }

    @Override
    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        super.collectionProcessComplete();
        if (this.isTraining()) {
            UIMAFramework.getLogger(GenericFHIRElementRelationExtractor.class).log(Level.INFO, relationType + ": " + posInstances + " true relations, " + negInstances + " false relations");
            try {
                JarClassifierBuilder.trainAndPackage(new File("/edu/mayo/bsi/nlp2fhir/nlp/relations/" + relationType + "/"), "-h", "0", "-w+1", "1", "-w-1", posInstances / (double) negInstances + "");
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }
}
