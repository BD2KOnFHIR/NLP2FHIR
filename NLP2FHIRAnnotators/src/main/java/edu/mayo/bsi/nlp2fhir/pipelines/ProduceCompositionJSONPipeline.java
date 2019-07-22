package edu.mayo.bsi.nlp2fhir.pipelines;

import edu.mayo.bsi.nlp2fhir.pipelines.serialization.SerializationPipelineBuilder;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRFileSystemReader;
import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.ohnlp.medtagger.cr.FileSystemReader;

import java.io.File;
import java.io.IOException;

/**
 * Produces Composition JSONs from raw text using supplied section annotations
 */
public class ProduceCompositionJSONPipeline {
    public static void main(String... args) throws UIMAException, IOException {
        System.setProperty("vocab.src.dir", System.getProperty("user.dir"));
        CollectionReaderDescription cr = CollectionReaderFactory.createReaderDescription(
                FHIRFileSystemReader.class,
                FileSystemReader.PARAM_INPUTDIR, "resources/evaluation/sharpn/text"
        );
        AggregateBuilder pipeline = new AggregateBuilder();
//        pipeline.add(AnalysisEngineFactory.createEngineDescription(SectionReader.class,
//                SectionReader.PARAM_PROJECT_FILE, "resources/evaluation/sharpn/sections/SECTION_ANNOTATIONS.pprj"));
        pipeline.add(SerializationPipelineBuilder
                .newBuilder(new File("empty_out"))
                .addFHIRJSONOutput()
                .build());
        SimplePipeline.runPipeline(cr, pipeline.createAggregateDescription());
    }
}
