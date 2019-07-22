package edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks;

import edu.mayo.bsi.nlp2fhir.common.FHIRJSONSchemaReader;
import edu.mayo.bsi.nlp2fhir.common.model.schema.FHIRSchema;
import edu.mayo.bsi.nlp2fhir.common.model.schema.SchemaResource;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.BuildablePipeline;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.PipelineTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.options.Option;
import edu.mayo.bsi.nlp2fhir.pipelines.serialization.SerializationPipelineBuilder;

import java.io.File;
import java.util.*;

public class SerializationTask implements PipelineTask {
    private static HashMap<String, List<Option>> SERIALIZATION_OPTIONS;

    private enum KEYS {
        OUTPUT_DIR,
        OUTPUT_XMI,
        OUTPUT_ANAFORA,
        OUTPUT_KNOWTATOR,
        OUTPUT_TEXT,
        OUTPUT_FHIR_BUNDLE,
        ANAFORA_ANNOTATOR_USERNAME,
        ANAFORA_CORPUS_NAME,
        ANAFORA_RESOURCES
    }

    static {
        FHIRSchema schema = FHIRJSONSchemaReader.readSchema();
        SERIALIZATION_OPTIONS = new LinkedHashMap<>();
        SERIALIZATION_OPTIONS.put(KEYS.OUTPUT_DIR.name(),
                Collections.singletonList(new Option("Output Directory", true, 0)));
        SERIALIZATION_OPTIONS.put(KEYS.OUTPUT_XMI.name(),
                Collections.singletonList(new Option("Create XMIs", true, 1, true, false)));
        SERIALIZATION_OPTIONS.put(KEYS.OUTPUT_TEXT.name(),
                Collections.singletonList(new Option("Create Text Documents", true, 1, true, false)));
        SERIALIZATION_OPTIONS.put(KEYS.OUTPUT_FHIR_BUNDLE.name(),
                Collections.singletonList(new Option("Create FHIR JSON Resources", true, 1, true, false)));
        SERIALIZATION_OPTIONS.put(KEYS.OUTPUT_ANAFORA.name(), Arrays.asList(
                new Option("Create Anafora Project", true, 1, true, false),
                new Option("Anafora Annotator Username", true, 0),
                new Option("Anafora Corpus Name", true, 0)
        ));
        SERIALIZATION_OPTIONS.put(KEYS.OUTPUT_KNOWTATOR.name(),
                Collections.singletonList(new Option("Create Knowtator Project", true, 1, true, false)));
    }

    @Override
    public Map<String, List<Option>> getOptions() {
        return SERIALIZATION_OPTIONS;
    }

    @Override
    public Map<String, List<Option>> getActiveOptions() {
        return getOptions();
    }

    @Override
    public void construct(BuildablePipeline pipeline) {
        pipeline.setSerializationTask(this);
        SerializationPipelineBuilder builder =
                SerializationPipelineBuilder.newBuilder(
                        new File(SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_DIR.name()).get(0).getValue()));

        if ((boolean) SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_XMI.name()).get(0).getSelected().get(0)) {
            builder.addXMIOutput();
        }
        if ((boolean) SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_TEXT.name()).get(0).getSelected().get(0)) {
            builder.addDocumentOutput();
        }
        if ((boolean) SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_FHIR_BUNDLE.name()).get(0).getSelected().get(0)) {
            builder.addFHIRJSONOutput();
        }
        Set<String> producedResources = new HashSet<>();
        for (Object o : pipeline.getResourceTask().getOptions().get(ResourceTask.PRODUCED_RESOURCE_OPTION_KEY).get(0).getSelected()) {
            producedResources.addAll(((ResourceTask.Invocation)o).getResources());
        }
        producedResources.add("Composition");
//        if ((boolean) SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_KNOWTATOR.name()).get(0).getSelected().get(0)) {
//            builder.addKnowtatorOutput(producedResources.toArray(new String[producedResources.size()])); // TODO
//        }
//        if ((boolean) SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_ANAFORA.name()).get(0).getSelected().get(0)) {
//            String username = SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_ANAFORA.name()).get(1).getValue();
//            String corpus = SERIALIZATION_OPTIONS.get(KEYS.OUTPUT_ANAFORA.name()).get(2).getValue();
//            @SuppressWarnings("SuspiciousToArrayCall") // Known to be correct by options being set
//            String[] resources = producedResources.toArray(new String[producedResources.size()]);
//            builder.addAnaforaOutput(username, corpus, resources); // TODO
//        }
        pipeline.getPipeline().add(builder.build());
    }

    @Override
    public String trackUpdateOption() {
        return null;
    }
}
