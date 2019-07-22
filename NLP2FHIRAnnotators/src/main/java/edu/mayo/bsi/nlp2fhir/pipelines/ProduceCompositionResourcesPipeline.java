package edu.mayo.bsi.nlp2fhir.pipelines;

import edu.mayo.bsi.nlp2fhir.pipelines.resources.ResourcePipelineBuilder;
import edu.mayo.bsi.nlp2fhir.pipelines.serialization.SerializationPipelineBuilder;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRJSONCompositionResourceReader;
import edu.mayo.bsi.nlp2fhir.pipelines.resources.ResourcePipelineBuilder;
import edu.mayo.bsi.nlp2fhir.pipelines.serialization.SerializationPipelineBuilder;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRJSONCompositionResourceReader;
import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.ResourceInitializationException;
import org.ohnlp.medtagger.cr.FileSystemReader;

import java.io.File;
import java.io.IOException;

public class ProduceCompositionResourcesPipeline {
    public static void main(String... args) throws UIMAException, IOException {
        System.setProperty("vocab.src.dir", System.getProperty("user.dir"));
        CollectionReaderDescription cr = CollectionReaderFactory.createReaderDescription(
                FHIRJSONCompositionResourceReader.class,
                FileSystemReader.PARAM_INPUTDIR, "out/fhir_resources/Composition"
        );
        AggregateBuilder pipeline = getPipeline();
        SimplePipeline.runPipeline(cr, pipeline.createAggregateDescription());
    }

    public static AggregateBuilder getPipeline() throws ResourceInitializationException {
        AggregateBuilder pipeline = new AggregateBuilder();
        pipeline.add(ResourcePipelineBuilder
                        .newBuilder(SourceNLPSystem.CTAKES, SourceNLPSystem.MEDTIME, SourceNLPSystem.MEDXN)
//                .addMedicationListResources()
                        .addProblemListResources()
                        .addFamilyHistoryResources()
                        .build()
        );
        pipeline.add(SerializationPipelineBuilder
                .newBuilder(new File("out"))
//                .addKnowtatorOutput("Condition", "Procedure", "Device", "MedicationStatement", "FamilyMemberHistory")
                .addXMIOutput()
                .addFHIRJSONOutput()
                .addDocumentOutput()
                .build());
        return pipeline;
    }
}
