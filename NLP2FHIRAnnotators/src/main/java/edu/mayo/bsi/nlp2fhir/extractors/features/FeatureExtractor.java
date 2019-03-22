package edu.mayo.bsi.nlp2fhir.extractors.features;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.cleartk.ml.Feature;

import java.util.List;

public interface FeatureExtractor<T1, T2> {
    List<Feature> extract(JCas cas, T1 arg1, T2 arg2) throws AnalysisEngineProcessException;
}
