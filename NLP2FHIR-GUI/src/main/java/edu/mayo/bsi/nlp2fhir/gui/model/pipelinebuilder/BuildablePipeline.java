package edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder;

import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.DeserializationTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.ResourceTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.SerializationTask;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.internal.ResourceManagerFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceManager;
import org.apache.uima.util.CasCreationUtils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Arrays.asList;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

/**
 * Represents an under-construction pipeline that can eventually be executed
 */
public class BuildablePipeline {
    private CollectionReaderDescription cr;
    private AggregateBuilder pipeline;

    private DeserializationTask deserializationTask;
    private ResourceTask resourceTask;
    private SerializationTask serializationTask;

    public BuildablePipeline() {
        this.cr = null;
        this.pipeline = new AggregateBuilder();
    }

    public CollectionReaderDescription getCollectionReader() {
        return cr;
    }

    public void setCollectionReader(CollectionReaderDescription cr) {
        this.cr = cr;
    }

    public AggregateBuilder getPipeline() {
        return pipeline;
    }

    public DeserializationTask getDeserializationTask() {
        return deserializationTask;
    }

    public ResourceTask getResourceTask() {
        return resourceTask;
    }

    public SerializationTask getSerializationTask() {
        return serializationTask;
    }

    public void setDeserializationTask(DeserializationTask deserializationTask) {
        this.deserializationTask = deserializationTask;
    }

    public void setResourceTask(ResourceTask resourceTask) {
        this.resourceTask = resourceTask;
    }

    public void setSerializationTask(SerializationTask serializationTask) {
        this.serializationTask = serializationTask;
    }

    public CompletableFuture<Boolean> executePipeline() {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (cr == null) {
            future.completeExceptionally(new IllegalArgumentException("Cannot run a pipeline without a collection reader!"));
        } else {
            ExecutorService threadPool = Executors.newFixedThreadPool(1); // TODO have options to run multiple simultaneous pipelines
            threadPool.submit(() -> {
                try {
                    runPipeline(cr, pipeline.createAggregateDescription());
                    future.complete(true);
                } catch (Throwable e) {
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            });
            threadPool.shutdown();
        }
        return future;
    }

    public static void runPipeline(final CollectionReaderDescription readerDesc,
                                   final AnalysisEngineDescription... descs) throws UIMAException, IOException {
        ResourceManager resMgr = ResourceManagerFactory.newResourceManager();

        // Create the components
        final CollectionReader reader = UIMAFramework.produceCollectionReader(readerDesc, resMgr, null);

        // Create AAE
        final AnalysisEngineDescription aaeDesc = createEngineDescription(descs);

        // Instantiate AAE
        final AnalysisEngine aae = UIMAFramework.produceAnalysisEngine(aaeDesc, resMgr, null);

        // Create CAS from merged metadata
        final CAS cas = CasCreationUtils.createCas(asList(reader.getMetaData(), aae.getMetaData()),
                null, resMgr);
        reader.typeSystemInit(cas.getTypeSystem());

        try {
            // Process
            while (reader.hasNext()) {
                reader.getNext(cas);
                try {
                    aae.process(cas);
                } catch (Throwable e) {
                    e.printStackTrace(); // Don't stop processing due to error in a document
                }
                cas.reset();
            }

            // Signal end of processing
            aae.collectionProcessComplete();
        } finally {
            // Destroy
            aae.destroy();
        }
    }
}
