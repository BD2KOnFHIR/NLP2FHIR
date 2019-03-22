package edu.mayo.bsi.nlp2fhir.pipelines;

import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRFileSystemReader;
import edu.mayo.bsi.nlp2fhir.evaluation.SHARPKnowtatorXMLReaderWrapper;
import edu.mayo.bsi.nlp2fhir.pipelines.resources.ResourcePipelineBuilder;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRFileSystemReader;
import org.apache.ctakes.core.ae.SHARPKnowtatorXMLReader;
import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.cleartk.ml.jar.JarClassifierBuilder;
import org.ohnlp.medtagger.cr.FileSystemReader;

import java.io.File;
import java.io.IOException;

/**
 * Re-trains various cTAKES components using input FHIR training data
 * TODO: use FHIR training data instead of SHARPN
 */
public class TrainingPipeline {
    public static void main(String... args) throws Exception {
        // Pipeline to extract FHIR elements using NLP
        CollectionReaderDescription cr = CollectionReaderFactory.createReaderDescription(
                FHIRFileSystemReader.class,
                FileSystemReader.PARAM_INPUTDIR, "resources/training/sharpn/text"
        );
        AggregateBuilder trainingPipeline = new AggregateBuilder();
        trainingPipeline.add(AnalysisEngineFactory.createEngineDescription(SHARPKnowtatorXMLReaderWrapper.class,
                SHARPKnowtatorXMLReader.PARAM_SET_DEFAULTS, false,
                SHARPKnowtatorXMLReader.PARAM_TEXT_DIRECTORY, "resources/training/sharpn/text"));
        trainingPipeline.add(ResourcePipelineBuilder.newBuilder(true, SourceNLPSystem.CTAKES).build());
        SimplePipeline.runPipeline(cr, trainingPipeline.createAggregateDescription());
        JarClassifierBuilder.trainAndPackage(new File("models_degree"), "-h", "0", "-c", "1000");
        JarClassifierBuilder.trainAndPackage(new File("models_location"), "-h", "0", "-c", "1000");
        JarClassifierBuilder.trainAndPackage(new File("models_modifier"), "-h", "0", "-c", "1000");
    }
}
