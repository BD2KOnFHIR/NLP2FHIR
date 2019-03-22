package edu.mayo.bsi.nlp2fhir.pipelines;

import org.apache.uima.UIMAException;

import java.io.IOException;

public class PlainTextAllResourcesPipeline {
    public static void main(String... args) throws UIMAException, IOException {
        System.setProperty("vocab.src.dir", System.getProperty("user.dir"));
        // Generate base composition JSONS
        ProduceCompositionJSONPipeline.main();
        // Populate with resources
        ProduceCompositionResourcesPipeline.main();
    }
}
