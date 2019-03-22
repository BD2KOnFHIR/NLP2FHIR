package edu.mayo.bsi.nlp2fhir.evaluation;

import edu.mayo.bsi.nlp2fhir.evaluation.api.ResourceEvaluationTask;
import edu.mayo.bsi.nlp2fhir.evaluation.evaluators.DeepSearchResourceEvaluator;
import edu.mayo.bsi.nlp2fhir.evaluation.api.ResourceEvaluationTask;
import edu.mayo.bsi.nlp2fhir.evaluation.evaluators.DeepSearchResourceEvaluator;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.Resource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class SHARPNGoldStandardAnalysisEngine extends JCasAnnotator_ImplBase {

    private Collection<ResourceEvaluationTask<?>> evaluations;
    @ConfigurationParameter(
            name = "RESULTS_FILE"
    )
    private File out;
    @ConfigurationParameter(
            name = "EVALUATION_CONFIG"
    )
    private String configString;
    public static final String RESULTS_FILE_PARAM = "RESULTS_FILE";
    public static final String CONFIG_JSON_PARAM = "EVALUATION_CONFIG";


    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.evaluations = new LinkedList<>();
        if (out == null) {
            throw new ResourceInitializationException(new RuntimeException("No evaluation out file specified!"));
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new ResourceInitializationException(new RuntimeException("Failed ot create output directory " + parent.getPath()));
            }
        }
        JSONObject config = new JSONObject(configString);

        for (Map.Entry<String, Object> e : config.toMap().entrySet()) {
            if (!(e.getValue() instanceof HashMap)) {
                throw new ResourceInitializationException(new IllegalArgumentException("Expected a path descriptor object, got " + e.getValue().toString()));
            }
            HashMap<String, Object> pathObj = (HashMap<String, Object>) e.getValue();
            ArrayList<String> paths = (ArrayList) pathObj.getOrDefault("paths", new ArrayList<>());
            List<String> pathList = new ArrayList<>(paths.size());
            for (Object o : paths) {
                pathList.add(o.toString());
            }
            Set<String> values = new HashSet<>();
            for (Object o : ((ArrayList) pathObj.getOrDefault("values", new ArrayList()))) {
                values.add(o.toString());
            }
            List<String> identifiers = new LinkedList<>();
            for (Object o : ((ArrayList) pathObj.getOrDefault("identifier", new ArrayList()))) {
                identifiers.add(o.toString());
            }
            Class<? extends Resource> evaluatorClazz;
            try {
                evaluatorClazz = (Class<? extends Resource>) Class.forName(e.getKey());
            } catch (ClassNotFoundException | ClassCastException e1) {
                throw new ResourceInitializationException(e1);
            }
            evaluations.add(new DeepSearchResourceEvaluator<>(evaluatorClazz, identifiers, pathList, values));
        }
    }

    @Override
    public void process(JCas resultView) throws AnalysisEngineProcessException {
        JCas goldView;
        try {
            goldView = resultView.getView("Gold Standard");
        } catch (CASException e) {
            throw new AnalysisEngineProcessException(e);
        }
        for (ResourceEvaluationTask<?> evaluation : evaluations) {
            evaluation.evaluate(goldView, resultView);
        }
    }

    @Override
    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        LinkedList<String> outQueue = new LinkedList<>();
        outQueue.addLast("Name\tPrecision\tRecall\tF1-Score\tTrue Positive\tFalse Positive\tFalse Negative");
        for (ResourceEvaluationTask<?> evaluation : evaluations) {
            outQueue.addAll(evaluation.generateResults());
        }

        try (FileWriter writer = new FileWriter(out)) {
            boolean first = true;
            for (String s : outQueue) {
                if (first) {
                    first = false;
                } else {
                    writer.write("\n");
                }
                writer.write(s);
            }
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

    }
}
