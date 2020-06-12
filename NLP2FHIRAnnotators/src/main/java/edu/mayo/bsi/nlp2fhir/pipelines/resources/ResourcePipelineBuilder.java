package edu.mayo.bsi.nlp2fhir.pipelines.resources;

import edu.mayo.bsi.nlp2fhir.extractors.ConTexTAssertionAnnotator;
import edu.mayo.bsi.nlp2fhir.extractors.GenericFHIRElementRelationExtractor;
import edu.mayo.bsi.nlp2fhir.extractors.SnomedCTDictionaryLookupExtractor;
import edu.mayo.bsi.nlp2fhir.transformers.CTAKESToFHIRFamilyMemberHistory;
import edu.mayo.bsi.nlp2fhir.transformers.CTAKESToFHIRMedications;
import edu.mayo.bsi.nlp2fhir.transformers.MedExtractorsToFHIRMedications;
import edu.mayo.bsi.nlp2fhir.transformers.MedTimeToFHIRMedications;
import edu.mayo.bsi.nlp2fhir.pipelines.PipelineDependency;
import edu.mayo.bsi.nlp2fhir.pipelines.SourceNLPSystem;
import edu.mayo.bsi.nlp2fhir.transformers.*;
import org.apache.ctakes.assertion.medfacts.cleartk.*;
import org.apache.ctakes.chunker.ae.Chunker;
import org.apache.ctakes.chunker.ae.adjuster.ChunkAdjuster;
import org.apache.ctakes.constituency.parser.ae.ConstituencyParser;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.SentenceDetector;
import org.apache.ctakes.core.ae.SimpleSegmentAnnotator;
import org.apache.ctakes.core.ae.TokenizerAnnotatorPTB;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.core.resource.FileResourceImpl;
import org.apache.ctakes.coreference.ae.DeterministicMarkableAnnotator;
import org.apache.ctakes.coreference.ae.MarkableHeadTreeCreator;
import org.apache.ctakes.coreference.ae.MarkableSalienceAnnotator;
import org.apache.ctakes.coreference.ae.MentionClusterCoreferenceAnnotator;
import org.apache.ctakes.dependency.parser.ae.ClearNLPDependencyParserAE;
import org.apache.ctakes.dependency.parser.ae.ClearNLPSemanticRoleLabelerAE;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.ctakes.lvg.ae.LvgAnnotator;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.relationextractor.ae.*;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.DirectoryDataWriterFactory;
import org.cleartk.ml.libsvm.LibSvmBooleanOutcomeDataWriter;
import org.cleartk.ml.libsvm.LibSvmStringOutcomeDataWriter;
import org.ohnlp.medtime.ae.MedTimeAnnotator;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ResourcePipelineBuilder {

    private AggregateBuilder pipeline;
    private Set<SourceNLPSystem> systems;
    private boolean isTraining;

    private ResourcePipelineBuilder(boolean isTraining, SourceNLPSystem... nlpSystems) {
        this.systems = new HashSet<>();
        this.systems.addAll(Arrays.asList(nlpSystems));
        this.pipeline = new AggregateBuilder();
        this.isTraining = isTraining;
        try {
            initializeSourceSystems();
        } catch (Exception e) {
            // Wrap runtime exception, errors should not occur assuming all resources present
            // but we don't want to ignore if one does occur
            throw new RuntimeException(e);
        }
    }

    private void initializeSourceSystems() throws Exception {
        if (systems.contains(SourceNLPSystem.MEDXN)) {
            pipeline.add(AnalysisEngineFactory.createEngineDescription("medxndesc.aggregate_analysis_engine.MedXNAggregateTAE"));
        }
        if (systems.contains(SourceNLPSystem.MEDTIME)) {
            pipeline.add(AnalysisEngineFactory.createEngineDescription(MedTimeAnnotator.class,
                    "Date", true, "Duration", true, "Time", true, "Set", true, "reportFormat", "i2b2", "Resource_dir", "resources/medtimeresources"
            ));
        }
        if (systems.contains(SourceNLPSystem.CTAKES)) {
            initializeCTAKESPipeline();
        }
    }

    private void initializeCTAKESPipeline() throws MalformedURLException, ResourceInitializationException, FileNotFoundException {
        // -- Base Token Processing
        pipeline.add(SimpleSegmentAnnotator.createAnnotatorDescription());
        pipeline.add(SentenceDetector.createAnnotatorDescription());
        pipeline.add(TokenizerAnnotatorPTB.createAnnotatorDescription());
        pipeline.add(LvgAnnotator.createAnnotatorDescription());
        pipeline.add(ContextDependentTokenizerAnnotator.createAnnotatorDescription());
        pipeline.add(POSTagger.createAnnotatorDescription());
        // -- Bundled Dictionary
//        if (!this.isTraining) { // If we're training mentions already loaded in via gold standard
            pipeline.add(AnalysisEngineFactory.createEngineDescription(DefaultJCasTermAnnotator.class,
                    "windowAnnotations", "org.apache.ctakes.typesystem.type.textspan.Sentence",
                    "DictionaryDescriptor", ExternalResourceFactory.createExternalResourceDescription(
                            FileResourceImpl.class,
                            FileLocator.locateFile("org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab.xml")
                    )
            ));

        pipeline.add(AnalysisEngineFactory.createEngineDescription(DefaultJCasTermAnnotator.class,
                "windowAnnotations", "org.apache.ctakes.typesystem.type.textspan.Sentence",
                "DictionaryDescriptor", ExternalResourceFactory.createExternalResourceDescription(
                        FileResourceImpl.class,
                        FileLocator.locateFile("org/apache/ctakes/dictionary/lookup/fast/covid_tui_cui.xml")
                )
        ));
//        }
//        pipeline.add(AnalysisEngineFactory.createEngineDescription(SideEffectAnnotator.class));
        // -- Coreference Processing
        pipeline.add(ConstituencyParser.createAnnotatorDescription());
        pipeline.add(AnalysisEngineFactory.createEngineDescription(DeterministicMarkableAnnotator.class));
        pipeline.add(AnalysisEngineFactory.createEngineDescription(MarkableHeadTreeCreator.class));
        pipeline.add(MarkableSalienceAnnotator.createAnnotatorDescription("/org/apache/ctakes/temporal/ae/salience/model.jar"));
        pipeline.add(MentionClusterCoreferenceAnnotator.createAnnotatorDescription("/org/apache/ctakes/coreference/models/mention-cluster/model.jar"));
        // -- Chunking
        pipeline.add(ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "NP"}, 1));
        pipeline.add(ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "PP", "NP"}, 2));
        pipeline.add(Chunker.createAnnotatorDescription());
        // -- Dependency Parsing
        pipeline.add(ClearNLPDependencyParserAE.createAnnotatorDescription());
        pipeline.add(AnalysisEngineFactory.createEngineDescription(ClearNLPSemanticRoleLabelerAE.class));
//        // -- Status and Negation
//        pipeline.add(AnalysisEngineFactory.createEngineDescription(ContextAnnotator.class,
//                ContextAnnotator.MAX_LEFT_SCOPE_SIZE_PARAM, 10,
//                ContextAnnotator.MAX_RIGHT_SCOPE_SIZE_PARAM, 10,
//                ContextAnnotator.CONTEXT_ANALYZER_CLASS_PARAM, "org.apache.ctakes.necontexts.status.StatusContextAnalyzer",
//                ContextAnnotator.CONTEXT_HIT_CONSUMER_CLASS_PARAM, "org.apache.ctakes.necontexts.status.StatusContextHitConsumer"));
//        pipeline.add(ContextAnnotator.createAnnotatorDescription());
//        // -- Uncertainty
//        pipeline.add(AnalysisEngineFactory.createEngineDescription(UncertaintyCleartkAnalysisEngine.class,
//                "classifierJarPath", "/org/apache/ctakes/assertion/models/uncertainty/model.jar",
//                "FEATURE_CONFIG", "DEP_REGEX",
//                "dataWriterFactoryClassName", "org.cleartk.ml.jar.DefaultDataWriterFactory",
//                "classifierFactoryClassName", "org.cleartk.ml.jar.JarClassifierFactory",
//                "PrintErrors", false
//        ));
        pipeline.add(AnalysisEngineFactory.createEngineDescription(ConTexTAssertionAnnotator.class));
        // -- Relations
        pipeline.add(AnalysisEngineFactory.createEngineDescription(ModifierExtractorAnnotator.class,
                "classifierJarPath", "/org/apache/ctakes/relationextractor/models/modifier_extractor/model.jar",
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME, LibSvmStringOutcomeDataWriter.class.getName(),
                DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY, new File("models_modifier"),
                CleartkAnnotator.PARAM_IS_TRAINING, false));
        pipeline.add(AnalysisEngineFactory.createEngineDescription(DegreeOfRelationExtractorAnnotator.class,
                DegreeOfRelationExtractorAnnotator.PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE, 0.5f,
                "classifierJarPath", "/org/apache/ctakes/relationextractor/models/degree_of/model.jar",
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME, LibSvmStringOutcomeDataWriter.class.getName(),
                DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY, new File("models_degree"),
                CleartkAnnotator.PARAM_IS_TRAINING, false));
        pipeline.add(AnalysisEngineFactory.createEngineDescription(LocationOfRelationExtractorAnnotator.class,
                LocationOfRelationExtractorAnnotator.PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE, 0.5f,
                "classifierJarPath", "/org/apache/ctakes/relationextractor/models/location_of/model.jar",
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME, LibSvmStringOutcomeDataWriter.class.getName(),
                DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY, new File("models_location"),
                CleartkAnnotator.PARAM_IS_TRAINING, false));
        // -- Assertions/Attributes
        pipeline.add(PolarityCleartkAnalysisEngine.createAnnotatorDescription());
        pipeline.add(UncertaintyCleartkAnalysisEngine.createAnnotatorDescription());
        pipeline.add(HistoryCleartkAnalysisEngine.createAnnotatorDescription());
        pipeline.add(ConditionalCleartkAnalysisEngine.createAnnotatorDescription());
        pipeline.add(GenericCleartkAnalysisEngine.createAnnotatorDescription());
        pipeline.add(SubjectCleartkAnalysisEngine.createAnnotatorDescription());
    }

    /*
     * Misc. NLP Steps
     */

    public ResourcePipelineBuilder addGenericRelationExtractor(Class<? extends Annotation> arg1, Class<? extends Annotation> arg2, String relName, String modelJarPath) throws ResourceInitializationException {
        // TODO validate not after resources are added to pipeline
        pipeline.add(AnalysisEngineFactory.createEngineDescription(GenericFHIRElementRelationExtractor.class,
                LocationOfRelationExtractorAnnotator.PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE, 0.5f,
                "classifierJarPath", modelJarPath,
                DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME, LibSvmBooleanOutcomeDataWriter.class.getName(),
                DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY, new File("/edu/mayo/bsi/nlp2fhir/nlp/relations/" + relName + "/"),
                CleartkAnnotator.PARAM_IS_TRAINING, this.isTraining,
                GenericFHIRElementRelationExtractor.RELATION_NAME_PARAM, relName,
                GenericFHIRElementRelationExtractor.RELATION_ARG1_CLASS_PARAM, arg1,
                GenericFHIRElementRelationExtractor.RELATION_ARG2_CLASS_PARAM, arg2));
        return this;
    }

    public ResourcePipelineBuilder addMiscellaneousPipelineEngine(AnalysisEngineDescription desc) {
        pipeline.add(desc);
        return this;
    }

    /*
     * Resource Generation Pipelines Below
     */

    /**
     * Adds pipelines for generation of {@link org.hl7.fhir.MedicationStatement} and {@link org.hl7.fhir.Medication}
     * resources within Medication List document sections
     *
     * @return The builder instance that the medication resource pipeline was added to
     */
    @PipelineDependency(
            required = {SourceNLPSystem.MEDXN, SourceNLPSystem.MEDTIME},
            recommended = {SourceNLPSystem.CTAKES}
    )
    public ResourcePipelineBuilder addMedicationListResources() throws ResourceInitializationException {
        // - MedXN and Resource Initialization Annotator
        pipeline.add(AnalysisEngineFactory.createEngineDescription(MedExtractorsToFHIRMedications.class));
        // - MedTime effective as of annotator
        pipeline.add(AnalysisEngineFactory.createEngineDescription(MedTimeToFHIRMedications.class));
        // -- Snomed NER for Dosage Instructions
        if (systems.contains(SourceNLPSystem.CTAKES)) {
            try {
                pipeline.add(AnalysisEngineFactory.createEngineDescription(DefaultJCasTermAnnotator.class,
                        "windowAnnotations", "org.apache.ctakes.typesystem.type.textspan.Sentence",
                        "DictionaryDescriptor", ExternalResourceFactory.createExternalResourceDescription(
                                FileResourceImpl.class,
                                FileLocator.locateFile("org/apache/ctakes/dictionary/lookup/fast/methodsanddosinginstructions.xml")
                        )
                ));
            } catch (FileNotFoundException var2) {
                var2.printStackTrace();
                throw new ResourceInitializationException(var2);
            }
            pipeline.add(AnalysisEngineFactory.createEngineDescription(CTAKESToFHIRMedications.class));
            pipeline.add(AnalysisEngineFactory.createEngineDescription(SnomedCTDictionaryLookupExtractor.class));
        }
        return this;
    }

    /**
     * Adds pipelines for generation of {@link org.hl7.fhir.Condition}, {@link org.hl7.fhir.Procedure}, and
     * {@link org.hl7.fhir.Device} resources within Problem List document sections
     *
     * @return The builder instance that the problem list pipeline was added to
     */
    @PipelineDependency(
            required = {SourceNLPSystem.CTAKES}
    )
    public ResourcePipelineBuilder addProblemListResources() throws ResourceInitializationException {
        pipeline.add(AnalysisEngineFactory.createEngineDescription(CTAKESToFHIRProblemList.class));
        return this;
    }

    /**
     * Adds pipelines for generation of {@link org.hl7.fhir.FamilyMemberHistory} resources within
     * Family/Social History document sections
     *
     * @return The builder instance that the problem list pipeline was added to
     */
    @PipelineDependency(
            required = {SourceNLPSystem.CTAKES}
    )
    public ResourcePipelineBuilder addFamilyHistoryResources() throws ResourceInitializationException {
        pipeline.add(AnalysisEngineFactory.createEngineDescription(CTAKESToFHIRFamilyMemberHistory.class));
        return this;
    }


    public AnalysisEngineDescription build() {
        try {
            return pipeline.createAggregateDescription();
        } catch (Exception e) {
            throw new RuntimeException(e); // Errors should not be possible, so don't force explicit handling
        }
    }

    public static ResourcePipelineBuilder newBuilder(SourceNLPSystem... nlpSystems) {
        return newBuilder(false, nlpSystems);
    }

    public static ResourcePipelineBuilder newBuilder(boolean train, SourceNLPSystem... nlpSystems) {
        return new ResourcePipelineBuilder(train, nlpSystems);
    }

}
