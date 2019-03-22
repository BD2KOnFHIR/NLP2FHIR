package edu.mayo.bsi.nlp2fhir.extractors.features;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.ml.Feature;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.ml.feature.extractor.FeatureExtractor1;
import org.cleartk.ml.feature.extractor.NamingExtractor1;
import org.cleartk.ml.feature.extractor.TypePathExtractor;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unchecked")
public class PartOfSpeechFeatureExtractor implements FeatureExtractor<Annotation, Annotation> {
    private FeatureExtractor1 mention1FeaturesExtractor;
    private FeatureExtractor1 mention2FeaturesExtractor;

    public PartOfSpeechFeatureExtractor() {
        FeatureExtractor1 pos = new TypePathExtractor(BaseToken.class, "partOfSpeech");
        FeatureExtractor1 tokenPOS = new CleartkExtractor(BaseToken.class, pos, new CleartkExtractor.Bag(new CleartkExtractor.Covered()));
        this.mention1FeaturesExtractor = new NamingExtractor1("mention1", tokenPOS);
        this.mention2FeaturesExtractor = new NamingExtractor1("mention2", tokenPOS);
    }

    public List<Feature> extract(JCas jCas, Annotation arg1, Annotation arg2) throws AnalysisEngineProcessException {
        List<Feature> features = new ArrayList();
        features.addAll(this.mention1FeaturesExtractor.extract(jCas, arg1));
        features.addAll(this.mention2FeaturesExtractor.extract(jCas, arg2));
        return features;
    }
}
