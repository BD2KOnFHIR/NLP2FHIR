package edu.mayo.bsi.nlp2fhir.extractors.features;

import org.apache.ctakes.relationextractor.ae.features.DependencyParseUtils;
import org.apache.ctakes.typesystem.type.syntax.ConllDependencyNode;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.ml.Feature;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DependencyPathFeatureExtractor implements FeatureExtractor<Annotation, Annotation> {

    @Override
    public List<Feature> extract(JCas jCas, Annotation arg1,
                                 Annotation arg2) throws AnalysisEngineProcessException {

        List<Feature> features = new ArrayList<Feature>();

        ConllDependencyNode node1 = DependencyParseUtils.findAnnotationHead(jCas, arg1);
        ConllDependencyNode node2 = DependencyParseUtils.findAnnotationHead(jCas, arg2);
        if (node1 == null || node2 == null) { return features; }

        List<LinkedList<ConllDependencyNode>> paths = DependencyParseUtils.getPathsToCommonAncestor(node1, node2);
        LinkedList<ConllDependencyNode> path1 = paths.get(0);
        LinkedList<ConllDependencyNode> path2 = paths.get(1);

        features.add(new Feature("DEPENDENCY_PATH_MEAN_DISTANCE_TO_COMMON_ANCESTOR", (path1.size() + path2.size()) / 2.0));
        features.add(new Feature("DEPENDENCY_PATH_MAX_DISTANCE_TO_COMMON_ANCESTOR", Math.max(path1.size(), path2.size())));
        features.add(new Feature("DEPENDENCY_PATH_MIN_DISTANCE_TO_COMMON_ANCESTOR", Math.min(path1.size(), path2.size())));

        LinkedList<ConllDependencyNode> node1ToNode2Path = DependencyParseUtils.getPathBetweenNodes(node1, node2);
        features.add(new Feature("DEPENDENCY_PATH", DependencyParseUtils.pathToString(node1ToNode2Path)));

        return features;
    }

}
