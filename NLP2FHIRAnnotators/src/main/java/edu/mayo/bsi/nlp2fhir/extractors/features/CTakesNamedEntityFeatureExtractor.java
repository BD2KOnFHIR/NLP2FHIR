package edu.mayo.bsi.nlp2fhir.extractors.features;

import org.apache.ctakes.typesystem.type.textsem.EntityMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.ml.Feature;
import org.cleartk.ml.feature.extractor.CleartkExtractorException;
import org.cleartk.ml.feature.extractor.DistanceExtractor;
import org.cleartk.ml.feature.extractor.FeatureExtractor1;
import org.cleartk.ml.feature.extractor.NamingExtractor1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unchecked")
public class CTakesNamedEntityFeatureExtractor implements FeatureExtractor<Annotation, Annotation>{

    private FeatureExtractor1 namedEntityType = new FeatureExtractor1() {
        @Override
        public List<Feature> extract(JCas jCas, Annotation ann) throws CleartkExtractorException {
            if (ann instanceof IdentifiedAnnotation) {
                IdentifiedAnnotation idAnn = (IdentifiedAnnotation) ann;
                return Collections.singletonList(new Feature("TypeID", String.valueOf(idAnn.getTypeID())));
            } else {
                return Collections.singletonList(new Feature("TypeID", "UNKNOWN_NOT_AN_NAMED_ENTITY"));
            }
        }
    };

    /**
     * All extractors for mention 1, with features named to distinguish them from mention 2
     */
    private FeatureExtractor1 mention1FeaturesExtractor = new NamingExtractor1(
            "mention1",
            namedEntityType);

    /**
     * All extractors for mention 2, with features named to distinguish them from mention 1
     */
    private FeatureExtractor1 mention2FeaturesExtractor = new NamingExtractor1(
            "mention2",
            namedEntityType);

    /**
     * Number of named entities between the two mentions
     */
    private DistanceExtractor nEntityMentionsBetween = new DistanceExtractor(null, EntityMention.class);

    @Override
    public List<Feature> extract(JCas jCas, Annotation arg1, Annotation arg2)
            throws AnalysisEngineProcessException {

        List<Feature> features = new ArrayList<>();
        features.addAll(this.mention1FeaturesExtractor.extract(jCas, arg1));
        features.addAll(this.mention2FeaturesExtractor.extract(jCas, arg2));
        features.addAll(this.nEntityMentionsBetween.extract(jCas, arg1, arg2));

        // entity type of both mentions, concatenated
        int type1;
        if (arg1 instanceof IdentifiedAnnotation) {
            type1 = ((IdentifiedAnnotation) arg1).getTypeID();
        } else {
            type1 = -99999;
        }
        int type2;
        if (arg2 instanceof IdentifiedAnnotation) {
            type2 = ((IdentifiedAnnotation) arg2).getTypeID();
        } else {
            type2 = -99999;
        }
        features.add(new Feature("type1type2", String.format("%s_%s", type1, type2)));

        // is mention1 included in mention2?
        boolean begins1After2 = arg1.getBegin() >= arg2.getBegin();
        boolean ends1Before2 = arg1.getEnd() <= arg2.getEnd();
        features.add(new Feature("mention1InMention2", begins1After2 && ends1Before2));

        // is mention2 included in mention1?
        boolean begins2After1 = arg2.getBegin() >= arg1.getBegin();
        boolean ends2Before1 = arg2.getEnd() <= arg1.getEnd();
        features.add(new Feature("mention2InMention1", begins2After1 && ends2Before1));

        return features;
    }


}
