package edu.mayo.bsi.nlp2fhir.extractors.features;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.ml.Feature;
import org.cleartk.ml.feature.extractor.*;

import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unchecked")
public class TokenFeatureExtractor implements FeatureExtractor<Annotation, Annotation> {

    private FeatureExtractor1 coveredText = new CoveredTextExtractor();
    private FeatureExtractor1 tokenContext = new CleartkExtractor(
            BaseToken.class,
            this.coveredText,
            new CleartkExtractor.FirstCovered(1),
            new CleartkExtractor.LastCovered(1),
            new CleartkExtractor.Bag(new CleartkExtractor.Covered()),
            new CleartkExtractor.Preceding(3),
            new CleartkExtractor.Following(3)
    );
    private FeatureExtractor1 arg1FeatureExtractor = new NamingExtractor1("arg1", new CombinedExtractor1(coveredText, tokenContext));
    private FeatureExtractor1 arg2FeatureExtractor = new NamingExtractor1("arg2", new CombinedExtractor1(coveredText, tokenContext));
    private CleartkExtractor tokensBetween = new CleartkExtractor(
            BaseToken.class,
            new NamingExtractor1("BetweenMentions", coveredText),
            new CleartkExtractor.FirstCovered(1),
            new CleartkExtractor.LastCovered(1),
            new CleartkExtractor.Bag(new CleartkExtractor.Covered())
    );
    private DistanceExtractor numTokensBetween = new DistanceExtractor(null, BaseToken.class);

    @Override
    public List<Feature> extract(JCas cas, Annotation arg1, Annotation arg2) throws AnalysisEngineProcessException {
        List<Feature> feats = new LinkedList<>();
        feats.addAll(arg1FeatureExtractor.extract(cas, arg1));
        feats.addAll(arg2FeatureExtractor.extract(cas, arg2));
        feats.addAll(tokensBetween.extractBetween(cas, arg1, arg2));
        feats.addAll(numTokensBetween.extract(cas, arg1, arg2));
        return feats;
    }
}
