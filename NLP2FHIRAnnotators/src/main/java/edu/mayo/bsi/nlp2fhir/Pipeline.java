package edu.mayo.bsi.nlp2fhir;

import edu.mayo.bsi.nlp2fhir.evaluation.GoldStandardEvaluationAnalysisEngine;
import edu.mayo.bsi.nlp2fhir.extractors.SectionExtractor;
import edu.mayo.bsi.nlp2fhir.extractors.SnomedCTDictionaryLookupExtractor;
//import edu.mayo.bsi.nlp2fhir.postprocessors.FHIR2KnowtatorPostProcessor;
import edu.mayo.bsi.nlp2fhir.postprocessors.XMIWriterPostProcessor;
//import edu.mayo.bsi.nlp2fhir.postprocessors.mayo.Sections2KnowtatorPostProcessor;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRXMIFileSystemReader;
import edu.mayo.bsi.nlp2fhir.transformers.CTAKESToFHIRMedications;
import edu.mayo.bsi.nlp2fhir.transformers.CTAKESToFHIRProblemList;
import edu.mayo.bsi.nlp2fhir.transformers.MedExtractorsToFHIRMedications;
import edu.mayo.bsi.nlp2fhir.transformers.MedTimeToFHIRMedications;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.core.resource.FileResourceImpl;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.pear.util.FileUtil;
import org.apache.uima.resource.ResourceInitializationException;
import org.ohnlp.medtagger.cr.FileSystemReader;
import org.ohnlp.medtime.ae.MedTimeAnnotator;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Main class containing builders for aggregate pipelines for various tasks. Also contains a main method that
 * runs all current annotators
 */
public class Pipeline {
    private static final String PATH_TO_EVAL_TMP = "eval";

    public static void main(String[] args) throws UIMAException, IOException, ClassNotFoundException {
        // Check and Initialize Required Resources
        System.setProperty("vocab.src.dir", System.getProperty("user.dir"));
        // Run UIMA pipeline
//        CollectionReaderDescription cr = CollectionReaderFactory.createReaderDescription(
//                FHIRFileSystemReader.class,
//                FileSystemReader.PARAM_INPUTDIR, "data"
//        );
        CollectionReaderDescription cr = CollectionReaderFactory.createReaderDescription(
                FHIRXMIFileSystemReader.class,
                FileSystemReader.PARAM_INPUTDIR, "xmis"
        );
        AggregateBuilder pipelineBuilder = new AggregateBuilder();
        // - Section detection
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(SectionExtractor.class));
        // - NLP Pipelines
        // -- MedExtractors
        AnalysisEngineDescription medXNDesc = AnalysisEngineFactory.createEngineDescription("medxndesc.aggregate_analysis_engine.MedXNAggregateTAE");
        pipelineBuilder.add(medXNDesc);
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(MedTimeAnnotator.class,
                "Date", true, "Duration", true, "Time", true, "Set", true, "reportFormat", "i2b2", "Resource_dir", "resources/medtimeresources"
        ));
        // -- CTAKES
        pipelineBuilder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());
        // - Run Dictionary in either Standalone or EntityMention->Consumer Pairs
        // -- Bundled
        try {
            pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(DefaultJCasTermAnnotator.class,
                    "windowAnnotations", "org.apache.ctakes.typesystem.type.textspan.Sentence",
                    "DictionaryDescriptor", ExternalResourceFactory.createExternalResourceDescription(
                            FileResourceImpl.class,
                            FileLocator.locateFile("org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab.xml")
                    )
            ));
        } catch (FileNotFoundException var2) {
            var2.printStackTrace();
            throw new ResourceInitializationException(var2);
        }
        // -- Snomed NER for Dosage Instructions
        try {
            pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(DefaultJCasTermAnnotator.class,
                    "windowAnnotations", "org.apache.ctakes.typesystem.type.textspan.Sentence",
                    "DictionaryDescriptor", ExternalResourceFactory.createExternalResourceDescription(
                            FileResourceImpl.class,
                            FileLocator.locateFile("org/apache/ctakes/dictionary/lookup/fast/methodsanddosinginstructions.xml")
                    )
            ));
        } catch (FileNotFoundException var2) {
            var2.printStackTrace();
            throw new ResourceInitializationException(var2);
        }
        // - Medication Pipelines
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(MedExtractorsToFHIRMedications.class));
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(MedTimeToFHIRMedications.class));
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(CTAKESToFHIRMedications.class));
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(SnomedCTDictionaryLookupExtractor.class));
        // - Problem List Pipelines
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(CTAKESToFHIRProblemList.class));
        // - Evaluation
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(GoldStandardEvaluationAnalysisEngine.class,
                GoldStandardEvaluationAnalysisEngine.KNOWTATOR_DEF, "fhir_annotation.pins",
                GoldStandardEvaluationAnalysisEngine.EVAL_DIR, PATH_TO_EVAL_TMP));
        // - Output
        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(XMIWriterPostProcessor.class,
                XMIWriterPostProcessor.PARAM_OUTPUT_DIR, "out"
        ));
//        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(Sections2KnowtatorPostProcessor.class));
//        pipelineBuilder.add(AnalysisEngineFactory.createEngineDescription(FHIR2KnowtatorPostProcessor.class,
//                "PROJECT_FILE", new File("protege_fhir_schema", "FHIR_SCHEMA.pprj").getPath(),
//                "OUTPUT_DIR", "fhir_knowtator_out"));
        AnalysisEngineDescription pipeline = pipelineBuilder.createAggregateDescription();
        SimplePipeline.runPipeline(cr, pipeline);

        // Perform Aggregation Tasks (Evaluation, Knowtator Generation)
        // - Aggregate Evaluation Results
        File f = new File(PATH_TO_EVAL_TMP);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new RuntimeException("Could not initialize temp directory for evaluation!");
            }
        }
        Map<String, ArrayList<Integer>> typeToValues = new HashMap<>(); // Stores evaluation results for aggregation, see GoldStandardEvaluationAnnotator for format
        File[] evalFiles = f.listFiles(new FileUtil.ExtFilenameFilter("eval"));
        if (evalFiles == null) {
            throw new RuntimeException("Could not access evaluation files!");
        }
        for (File evalFile : evalFiles) {
            String name = evalFile.getName().substring(0, evalFile.getName().length() - 5);
            String type = name.split("-_-_-")[1];
            if (!typeToValues.containsKey(type)) {
                ArrayList<Integer> in = new ArrayList<>();
                typeToValues.put(type, in);
                for (int i = 0; i < 3; i++) {
                    in.add(0);
                }
            }
            BufferedReader reader = new BufferedReader(new FileReader(evalFile));
            String line = reader.readLine();
            String[] parsed = line.split("\\|");
            ArrayList<Integer> aggregate = typeToValues.get(type);
            for (int i = 0; i < 3; i++) {
                aggregate.set(i, aggregate.get(i) + Integer.valueOf(parsed[i]));
            }
        }
        // - Combine Knowtator annotations
        StringBuilder sB = new StringBuilder();
        File[] tmpFiles = new File("temp").listFiles(new FileUtil.ExtFilenameFilter("tmp"));
        if (tmpFiles == null) {
            throw new RuntimeException("Could not access generated temp files!");
        }
        for (File tmp : tmpFiles) {
            BufferedReader reader = new BufferedReader(new FileReader(tmp));
            String line;
            while ((line = reader.readLine()) != null) {
                sB.append(line).append("\n");
            }
            reader.close();
        }
        File knowtator = new File("GeneratedKnowtator.txt");
        FileWriter writer = new FileWriter(knowtator);
        writer.write(sB.toString());
        writer.flush();
        writer.close();
        // Output evaluation results
        FileWriter resultsWriter = new FileWriter(new File("evaluation.results"));
        ArrayList<String> keys = new ArrayList<>(typeToValues.keySet());
        keys.sort(String::compareTo);
        for (String s : keys) {
            resultsWriter.write("===============================\n");
            resultsWriter.write(s + "\n");
            resultsWriter.write("===============================\n");
            ArrayList<Integer> aggregate = typeToValues.get(s);
            // NLP2FHIR
            double truePositiveNLP2FHIR = aggregate.get(0);
            double falsePositiveNLP2FHIR = aggregate.get(1);
            double falseNegativeNLP2FHIR = aggregate.get(2);
            double recallNLP2FHIR = truePositiveNLP2FHIR / (truePositiveNLP2FHIR + falseNegativeNLP2FHIR);
            double precisionNLP2FHIR = truePositiveNLP2FHIR / (truePositiveNLP2FHIR + falsePositiveNLP2FHIR);
            double f1NLP2FHIR = 2 * ((precisionNLP2FHIR * recallNLP2FHIR) / (precisionNLP2FHIR + recallNLP2FHIR));
            resultsWriter.write("NLP2FHIR Results:\n");
            resultsWriter.write("- Recall: " + recallNLP2FHIR + "\n");
            resultsWriter.write("- Precision: " + precisionNLP2FHIR + "\n");
            resultsWriter.write("- F1-Score: " + f1NLP2FHIR + "\n");
            resultsWriter.write("- True Positives: " + truePositiveNLP2FHIR + "\n");
            resultsWriter.write("- False Positives: " + falsePositiveNLP2FHIR + "\n");
            resultsWriter.write("- False Negatives: " + falseNegativeNLP2FHIR + "\n");
            resultsWriter.write("\n");
            resultsWriter.flush();
        }
        resultsWriter.close();
    }

}
