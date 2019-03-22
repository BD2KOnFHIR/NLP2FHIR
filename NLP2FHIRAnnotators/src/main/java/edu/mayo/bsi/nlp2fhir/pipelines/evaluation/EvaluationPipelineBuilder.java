package edu.mayo.bsi.nlp2fhir.pipelines.evaluation;

import edu.mayo.bsi.nlp2fhir.evaluation.SHARPNGoldStandardAnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.Resource;
import org.json.JSONObject;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class EvaluationPipelineBuilder {

    private Map<String, Map<String, Collection<String>>> backingMap;
    private String outputPath;

    private EvaluationPipelineBuilder() {
        this.backingMap = new HashMap<>();
        this.outputPath = "evaluation-results.tsv";
    }

    public EvaluationPipelineBuilder setOutputFile(String path) {
        this.outputPath = path;
        return this;
    }

    public EvaluationPipelineBuilder addIdentifierMatch(Class<? extends Resource> clazz, MatchType matchType,  String... paths) {
        for (String path : paths) {
            backingMap.computeIfAbsent(clazz.getName(), k -> new HashMap<>()).computeIfAbsent("identifier", k -> new LinkedList<>()).add(path);
            switch (matchType) {
                case VALUE:
                    addNewValueMatches(clazz, paths);
                    break;
                case POSITION:
                default:
                    addNewPositionMatches(clazz, paths);
            }
        }
        return this;
    }

    public EvaluationPipelineBuilder addNewPositionMatches(Class<? extends Resource> clazz, String... paths) {
        for (String path : paths) {
            backingMap.computeIfAbsent(clazz.getName(), k -> new HashMap<>()).computeIfAbsent("paths", k -> new LinkedList<>()).add(path);
            backingMap.computeIfAbsent(clazz.getName(), k -> new HashMap<>()).computeIfAbsent("values", k -> new LinkedList<>()); // Initiate both just in case
        }
        return this;
    }

    public EvaluationPipelineBuilder addNewValueMatches(Class<? extends Resource> clazz, String... paths) {
        for (String path : paths) {
            backingMap.computeIfAbsent(clazz.getName(), k -> new HashMap<>()).computeIfAbsent("paths", k -> new LinkedList<>()).add(path);
            backingMap.computeIfAbsent(clazz.getName(), k -> new HashMap<>()).computeIfAbsent("values", k -> new LinkedList<>()).add(path);
        }
        return this;
    }

    public AnalysisEngineDescription build() {
        try {
            return AnalysisEngineFactory.createEngineDescription(SHARPNGoldStandardAnalysisEngine.class,
                    SHARPNGoldStandardAnalysisEngine.CONFIG_JSON_PARAM, new JSONObject(this.backingMap).toString(),
                    SHARPNGoldStandardAnalysisEngine.RESULTS_FILE_PARAM, new File(this.outputPath));
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }

    public static EvaluationPipelineBuilder newBuilder() {
        return new EvaluationPipelineBuilder();
    }

    public enum MatchType {
        POSITION,
        VALUE
    }
}
