package edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks;

import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.BuildablePipeline;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.PipelineTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.options.Option;
import edu.mayo.bsi.nlp2fhir.pipelines.SourceNLPSystem;
import edu.mayo.bsi.nlp2fhir.pipelines.resources.ResourcePipelineBuilder;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.*;
import java.util.function.Function;

public class ResourceTask implements PipelineTask {
    private static HashMap<String, List<Option>> RESOURCE_OPTIONS;

    private enum KEYS {
        RESOURCES_TO_PRODUCE
    }

    public static final String PRODUCED_RESOURCE_OPTION_KEY = KEYS.RESOURCES_TO_PRODUCE.name();

    static {
        RESOURCE_OPTIONS = new LinkedHashMap<>();
        RESOURCE_OPTIONS.put(KEYS.RESOURCES_TO_PRODUCE.name(),
                Collections.singletonList(
                        new Option("Resources to Produce",
                                true,
                                -1,
                                new Invocation("Medication List Resources", c -> {
                                    try {
                                        return c.addMedicationListResources();
                                    } catch (ResourceInitializationException e) {
                                        throw new RuntimeException(e);
                                    }
                                }, "MedicationStatement", "Medication"),
                                new Invocation("Procedures and Conditions", c -> {
                                    try {
                                        return c.addProblemListResources();
                                    } catch (ResourceInitializationException e) {
                                        throw new RuntimeException(e);
                                    }
                                }, "Procedure", "Condition"),
                                new Invocation("Family Medical History", c -> {
                                    try {
                                        return c.addFamilyHistoryResources();
                                    } catch (ResourceInitializationException e) {
                                        throw new RuntimeException(e);
                                    }
                                }, "FamilyMemberHistory"),
                                new Invocation("Observation Resources", c -> {
                                    try {
                                        return c.addObservationResources();
                                    } catch (ResourceInitializationException e) {
                                        throw new RuntimeException(e);
                                    }
                                }, "Observation")
                        )));
    }


    @Override
    public Map<String, List<Option>> getOptions() {
        return RESOURCE_OPTIONS;
    }

    @Override
    public Map<String, List<Option>> getActiveOptions() {
        return getOptions();
    }

    @Override
    public void construct(BuildablePipeline pipeline) {
        ResourcePipelineBuilder builder = ResourcePipelineBuilder.newBuilder(false, SourceNLPSystem.CTAKES, SourceNLPSystem.MEDTIME, SourceNLPSystem.MEDXN);
        for (Object o : RESOURCE_OPTIONS.get(KEYS.RESOURCES_TO_PRODUCE.name()).get(0).getSelected()) {
            ((Invocation)o).callable.apply(builder);
        }
        pipeline.getPipeline().add(builder.build());
        pipeline.setResourceTask(this);
    }

    @Override
    public String trackUpdateOption() {
        return null;
    }


    public static class Invocation {
        private String name;
        private Function<ResourcePipelineBuilder, ResourcePipelineBuilder> callable;
        private Collection<String> resources;

        Invocation(String name, Function<ResourcePipelineBuilder, ResourcePipelineBuilder> callable, String... resources) {
            this.name = name;
            this.callable = callable;
            this.resources = Arrays.asList(resources);
        }

        public String toString() {
            return name;
        }

        public Collection<String> getResources() {
            return resources;
        }
    }
}
