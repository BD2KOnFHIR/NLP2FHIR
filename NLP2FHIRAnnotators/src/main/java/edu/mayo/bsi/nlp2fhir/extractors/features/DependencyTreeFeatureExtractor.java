package edu.mayo.bsi.nlp2fhir.extractors.features;

import org.apache.ctakes.relationextractor.ae.features.DependencyParseUtils;
import org.apache.ctakes.typesystem.type.syntax.ConllDependencyNode;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.ml.Feature;

import java.util.ArrayList;
import java.util.List;

public class DependencyTreeFeatureExtractor implements FeatureExtractor<Annotation, Annotation> {

    @Override
    public List<Feature> extract(JCas jCas, Annotation arg1,
                                 Annotation arg2) throws AnalysisEngineProcessException {

        List<Feature> features = new ArrayList<Feature>();
        features.addAll(extractForNode(jCas, arg1, "MENTION1"));
        features.addAll(extractForNode(jCas, arg2, "MENTION2"));
        return features;
    }

    public static List<Feature> extractForNode(JCas jCas, Annotation mention, String ftrPrefix) {
        List<Feature> features = new ArrayList<Feature>();
        ConllDependencyNode mentionHeadNode = DependencyParseUtils.findAnnotationHead(jCas, mention);

        if (mentionHeadNode != null) {
            ConllDependencyNode dependsOn = mentionHeadNode.getHead();
            if (dependsOn != null) {
                features.add(new Feature(ftrPrefix + "_DEPENDS_ON_WORD", dependsOn.getCoveredText()));
                features.add(new Feature(ftrPrefix + "_DEPENDS_ON_POS", dependsOn.getPostag()));
                // Following features come from Zhou et al. 2005
                // ET1DW1: combination of the entity type and the dependent word for M1
                features.add(new Feature(ftrPrefix + "_TYPE-GOVERNING_WORD", String.format("%d-%s", mention instanceof IdentifiedAnnotation ? ((IdentifiedAnnotation) mention).getTypeID() : -99999, dependsOn.getCoveredText())));
                // H1DW1: combination of the head word and the dependent word for M1
                features.add(new Feature(ftrPrefix + "_HEAD_WORD-GOVERNING_WORD", String.format("%s-%s", mentionHeadNode.getCoveredText(), dependsOn.getCoveredText())));
                features.add(new Feature(ftrPrefix + "_TYPE-GOVERNING_POS", String.format("%d-%s", mention instanceof IdentifiedAnnotation ? ((IdentifiedAnnotation) mention).getTypeID() : -99999, dependsOn.getPostag())));
                features.add(new Feature(ftrPrefix + "_HEAD_POS-GOVERNING_POS", String.format("%s-%s", mentionHeadNode.getPostag(), dependsOn.getPostag())));
            }
        }
        return features;
    }
}
