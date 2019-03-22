package edu.mayo.bsi.nlp2fhir.pipelines.serialization;

import edu.mayo.bsi.nlp2fhir.anafora.serialization.CAS2AnaforaAnalysisEngine;
import edu.mayo.bsi.nlp2fhir.knowtator.KnowtatorFHIROntologyClassdefGenerator;
import edu.mayo.bsi.nlp2fhir.postprocessors.CAS2FHIRJSONPostProcessor;
import edu.mayo.bsi.nlp2fhir.postprocessors.DocumentTextContentPostProcessor;
import edu.mayo.bsi.nlp2fhir.postprocessors.FHIR2KnowtatorPostProcessor;
import edu.mayo.bsi.nlp2fhir.postprocessors.XMIWriterPostProcessor;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;

public class SerializationPipelineBuilder {
    private AggregateBuilder pipeline;
    private File outputDirectory;

    private SerializationPipelineBuilder(File outputDirectory) {
        this.outputDirectory = outputDirectory;
        this.pipeline = new AggregateBuilder();
        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                throw new IllegalArgumentException("Could not create output directory!");
            }
        }
    }

    public static SerializationPipelineBuilder newBuilder(File outputDir) {
        return new SerializationPipelineBuilder(outputDir);
    }

    public AnalysisEngineDescription build() {
        try {
            return pipeline.createAggregateDescription();
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serialization Pipelines
     */
    public SerializationPipelineBuilder addKnowtatorOutput(String... resourcesToProduce) {
        File out = new File(outputDirectory, "knowtator");
        if (!out.exists()) {
            if (!out.mkdirs()) {
                throw new IllegalArgumentException("Could not create knowtator output dir");
            }
        }
        try {
            KnowtatorFHIROntologyClassdefGenerator.main(resourcesToProduce);
            pipeline.add(AnalysisEngineFactory.createEngineDescription(FHIR2KnowtatorPostProcessor.class,
                    FHIR2KnowtatorPostProcessor.PARAM_FHIRPPRJ, new File("protege_fhir_schema", "FHIR_SCHEMA.pprj"),
                    FHIR2KnowtatorPostProcessor.PARAM_OUTPUT_DIR, out));
            return this;
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }

    public SerializationPipelineBuilder addAnaforaOutput(String username, String corpusname, String... resources) {
        File out = new File(outputDirectory, "anafora");
        if (!out.exists()) {
            if (!out.mkdirs()) {
                throw new IllegalArgumentException("Could not create anafora output dir");
            }
        }
        try {
            pipeline.add(AnalysisEngineFactory.createEngineDescription(CAS2AnaforaAnalysisEngine.class,
                    CAS2AnaforaAnalysisEngine.PARAM_OUTPUT_DIR, out,
                    CAS2AnaforaAnalysisEngine.PARAM_ANAFORA_CORPUSNAME, corpusname,
                    CAS2AnaforaAnalysisEngine.PARAM_ANAFORA_USERNAME, username,
                    CAS2AnaforaAnalysisEngine.PARAM_TRANSLATED_RESOURCES, resources));
            return this;
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }

    public SerializationPipelineBuilder addFHIRJSONOutput() {
        File out = new File(outputDirectory, "fhir_resources");
        if (!out.exists()) {
            if (!out.mkdirs()) {
                throw new IllegalArgumentException("Could not create knowtator output dir");
            }
        }
        try {
            pipeline.add(AnalysisEngineFactory.createEngineDescription(CAS2FHIRJSONPostProcessor.class,
                    CAS2FHIRJSONPostProcessor.PARAM_OUTPUT_DIR, out));
            return this;
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }

    public SerializationPipelineBuilder addXMIOutput() {
        try {
            pipeline.add(AnalysisEngineFactory.createEngineDescription(XMIWriterPostProcessor.class,
                    XMIWriterPostProcessor.PARAM_OUTPUT_DIR, outputDirectory // Don't need to create an output subdir, consumer does for us already
            ));
            return this;
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }

    public SerializationPipelineBuilder addDocumentOutput() {
        File out = new File(outputDirectory, "text");
        if (!out.exists()) {
            if (!out.mkdirs()) {
                throw new IllegalArgumentException("Could not create knowtator output dir");
            }
        }
        try {
            pipeline.add(AnalysisEngineFactory.createEngineDescription(DocumentTextContentPostProcessor.class,
                    DocumentTextContentPostProcessor.PARAM_OUTPUT_DIR, out));
            return this;
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }

}
