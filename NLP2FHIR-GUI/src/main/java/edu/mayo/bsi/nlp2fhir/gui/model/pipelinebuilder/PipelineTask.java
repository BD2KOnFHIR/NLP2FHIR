package edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder;

import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.options.Option;

import java.util.List;
import java.util.Map;

/**
 * Represents a pipeline task
 */
public interface PipelineTask {
    /**
     * @return A name->{@link Option} group map for this task, may change if {@link #trackUpdateOption()} is not null
     */
    Map<String, List<Option>> getOptions();

    /**
     * @return A listing of options active
     */
    Map<String, List<Option>> getActiveOptions();

    /**
     * Adds this task to a pipeline
     * @param pipeline The pipeline to add this task to
     */
    void construct(BuildablePipeline pipeline);

    /**
     * Indicates that the value of this key should be followed to refresh updates to option listings
     * @return The key of the option to track, or null if not tracked
     */
    String trackUpdateOption();
}
