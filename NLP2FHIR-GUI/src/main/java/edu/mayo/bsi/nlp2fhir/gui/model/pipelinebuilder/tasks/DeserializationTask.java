package edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks;

import edu.mayo.bsi.nlp2fhir.extractors.SectionExtractor;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.BuildablePipeline;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.PipelineTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.options.Option;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRFileSystemReader;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRJSONCompositionResourceReader;
import edu.mayo.bsi.nlp2fhir.preprocessors.FHIRXMIFileSystemReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.util.*;

public class DeserializationTask implements PipelineTask {

    private static HashMap<String, List<Option>> DESERIALIZATION_OPTIONS;
    private static HashMap<String, List<Option>> DESERIALIZATION_OPTIONS_ABRIDGED;

    private enum KEYS {
        INPUT_DIRECTORY,
        INPUT_TYPE,
        SECTION_DEFINITION_DIR,
        SECTION_DEFINITION_FILE
    }

    static {
        DESERIALIZATION_OPTIONS = new LinkedHashMap<>();
        DESERIALIZATION_OPTIONS.put(KEYS.INPUT_DIRECTORY.name(),
                Collections.singletonList(new Option("Input Directory", true, 0)));
        DESERIALIZATION_OPTIONS.put(KEYS.INPUT_TYPE.name(),
                Collections.singletonList(
                        new Option("Input Type",
                                true,
                                1,
                                "COMPOSITION_RESOURCE", "BUNDLE_RESOURCE", "XMI", "TEXT")));
        DESERIALIZATION_OPTIONS_ABRIDGED = new LinkedHashMap<>(DESERIALIZATION_OPTIONS);
        DESERIALIZATION_OPTIONS.put(KEYS.SECTION_DEFINITION_DIR.name(),
                Collections.singletonList(
                        new Option("Section Definition Directory",
                                false,
                                0)));
        DESERIALIZATION_OPTIONS.put(KEYS.SECTION_DEFINITION_FILE.name(),
                Collections.singletonList(
                        new Option("Section Definition File",
                                false,
                                0)));
    }

    @Override
    public Map<String, List<Option>> getOptions() {
        return DESERIALIZATION_OPTIONS;
    }

    @Override
    public Map<String, List<Option>> getActiveOptions() {
        if (DESERIALIZATION_OPTIONS.get(KEYS.INPUT_TYPE.name()).get(0).getSelected().size() == 0) {
            return DESERIALIZATION_OPTIONS_ABRIDGED;
        }
        switch (DESERIALIZATION_OPTIONS.get(KEYS.INPUT_TYPE.name()).get(0).getSelected().get(0).toString()) {
            case "COMPOSITION_RESOURCE":
            case "BUNDLE_RESOURCE": {
                return DESERIALIZATION_OPTIONS_ABRIDGED;
            }
            default: {
                return DESERIALIZATION_OPTIONS;
            }

        }
    }

    @Override
    public void construct(BuildablePipeline pipeline) {
        String inputDirPath = DESERIALIZATION_OPTIONS.get(KEYS.INPUT_DIRECTORY.name()).get(0).getValue();
        if (inputDirPath == null) {
            return; // TODO
        }
        pipeline.setDeserializationTask(this);
        File inputDir = new File(inputDirPath);
        boolean mustDefineSections = false;
        String type;
        List<Object> selected = DESERIALIZATION_OPTIONS.get(KEYS.INPUT_TYPE.name()).get(0).getSelected();
        if (selected.size() == 0) {
            type = "COMPOSITION_RESOURCE";
        } else {
            type = selected.get(0).toString();
        }
        switch (type) {
            case "COMPOSITION_RESOURCE": {
                try {
                    pipeline.setCollectionReader(CollectionReaderFactory.createReaderDescription(
                            FHIRJSONCompositionResourceReader.class,
                            FHIRJSONCompositionResourceReader.PARAM_INPUTDIR,
                            inputDir));
                    break;
                } catch (ResourceInitializationException e) {
                    throw new RuntimeException(e);
                }
            }
            case "BUNDLE_RESOURCE":
            default: {
                throw new UnsupportedOperationException(); // TODO
            }
            case "XMI": {
                try {
                    pipeline.setCollectionReader(CollectionReaderFactory.createReaderDescription(
                            FHIRXMIFileSystemReader.class,
                            FHIRXMIFileSystemReader.PARAM_INPUTDIR,
                            inputDir));
                    mustDefineSections = true;
                    break;
                } catch (ResourceInitializationException e) {
                    throw new RuntimeException(e);
                }
            }
            case "TEXT": {
                try {
                    pipeline.setCollectionReader(CollectionReaderFactory.createReaderDescription(
                            FHIRFileSystemReader.class,
                            FHIRFileSystemReader.PARAM_INPUTDIR,
                            inputDir));
                    mustDefineSections = true;
                    break;
                } catch (ResourceInitializationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (mustDefineSections) {
            String sectionDefinitionTSVPath = DESERIALIZATION_OPTIONS.get(KEYS.SECTION_DEFINITION_FILE.name()).get(0).getValue();
            try {
                pipeline.getPipeline().add(AnalysisEngineFactory.createEngineDescription(SectionExtractor.class, SectionExtractor.SECTION_DEFINITION_PARAM, sectionDefinitionTSVPath));
            } catch (ResourceInitializationException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String trackUpdateOption() {
        return "Input Type";
    }
    // TODO add section detector options
}
