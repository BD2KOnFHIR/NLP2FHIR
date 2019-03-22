package edu.mayo.bsi.nlp2fhir.stream;

import edu.mayo.bsi.nlp2fhir.pipelines.SourceNLPSystem;
import edu.mayo.bsi.nlp2fhir.pipelines.resources.ResourcePipelineBuilder;
import edu.mayo.bsi.uima.server.api.UIMAServer;
import edu.mayo.bsi.uima.server.api.UIMAServerPlugin;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class NLP2FHIRPlugin implements UIMAServerPlugin {
    @Override
    public String getName() {
        return "nlp2fhir";
    }

    @Override
    public void onEnable(UIMAServer server) {
        URLClassLoader cL = (URLClassLoader) ClassLoader.getSystemClassLoader();
        try {
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            File resourcesDir = new File("resources");
            addURL.invoke(cL, resourcesDir.toURI().toURL());
            AggregateBuilder extractionPipeline = new AggregateBuilder();
            extractionPipeline.add(AnalysisEngineFactory.createEngineDescription(CompositionCasInitializer.class));
            extractionPipeline.add(ResourcePipelineBuilder
                    .newBuilder(SourceNLPSystem.CTAKES, SourceNLPSystem.MEDTIME, SourceNLPSystem.MEDXN)
                    .addMedicationListResources()
                    .addProblemListResources()
                    .build(), "_InitialView", "text"
            );
            server.registerStream("nlp2fhir", null, extractionPipeline.createAggregateDescription());
            server.registerSerializer("nlp2fhir", new NLP2FHIRResourceBundleSerializer());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | MalformedURLException | ResourceInitializationException e) {
            e.printStackTrace();
        }


    }
}
