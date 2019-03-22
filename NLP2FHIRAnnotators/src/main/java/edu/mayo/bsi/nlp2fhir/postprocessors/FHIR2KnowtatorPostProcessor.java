package edu.mayo.bsi.nlp2fhir.postprocessors;

import edu.stanford.smi.protege.model.*;
import edu.stanford.smi.protege.model.Slot;
import edu.uchsc.ccp.knowtator.*;
import edu.uchsc.ccp.knowtator.textsource.TextSourceAccessException;
import edu.uchsc.ccp.knowtator.util.ProjectUtil;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Outputs CAS data structures to FHIR Knowtator annotations -- NOT thread safe!
 */
public class FHIR2KnowtatorPostProcessor extends JCasAnnotator_ImplBase {

    private KnowtatorManager knowtator;
    private AnnotationUtil annotationUtil;
    private MentionUtil mentionUtil;
    private Project project;
    @SuppressWarnings("WeakerAccess")
    @ConfigurationParameter(
            name = "PROJECT_FILE",
            description = "The project file containing FHIR element definitions"
    )
    public File pprjFile; // TODO autogenerate this using KnowtatorUtil?

    @SuppressWarnings("WeakerAccess")
    @ConfigurationParameter(
            name = "OUTPUT_DIR"
    )
    public File outDir;

    public static final String PARAM_FHIRPPRJ = "PROJECT_FILE";
    public static final String PARAM_OUTPUT_DIR = "OUTPUT_DIR";
    private long docs = 0;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) {
                throw new IllegalStateException("Could not create knowtator output directory");
            }
        }
        project = ProjectUtil.openProject(pprjFile.getAbsolutePath());
        try {
            ProjectUtil.saveProjectAs(project, new File(outDir, "FHIR_ANNOTATED_RESOURCES.pprj"));
            project = ProjectUtil.openProject(new File(outDir, "FHIR_ANNOTATED_RESOURCES.pprj").getAbsolutePath());
        } catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
        KnowledgeBase kB = project.getKnowledgeBase();
        KnowtatorProjectUtil util = new KnowtatorProjectUtil(kB);
        knowtator = new KnowtatorManager(util);
        annotationUtil = new AnnotationUtil(knowtator);
        mentionUtil = new MentionUtil(knowtator.getKnowtatorProjectUtil());
        mentionUtil.setAnnotationUtil(annotationUtil);
        annotationUtil.setMentionUtil(mentionUtil);
    }

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
        try {
            copyConditions(cas);
            copyProcedures(cas);
            copyFamilyMemberHistory(cas);
            if (++docs % 20 == 0) {
                save(); // Incremental save every 20 docs
            }
        } catch (TextSourceAccessException e) {
            throw new AnalysisEngineProcessException("Error creating Knowtator annotations!", null, e);
        }
    }

    private void copyConditions(JCas cas) throws TextSourceAccessException {
        for (Condition condition : JCasUtil.select(cas, Condition.class)) {
            SimpleInstance conditionInstance = annotationUtil.createAnnotation(
                    getClass("Condition"),
                    Collections.singletonList(new Span(condition.getBegin(), condition.getEnd())),
                    condition.getCoveredText(),
                    JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID());
            // - Condition Code
            if (condition.getCode() != null) {
                setSlotInstanceReference(conditionInstance,
                        "has_Condition.code",
                        copyCodeableConcept(condition.getCode(), "Condition.code",
                        JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID()));
            }
            // - Body Site
            if (condition.getBodySite() != null && condition.getBodySite().size() > 0) {
                LinkedList<Object> conceptInstances = new LinkedList<>();
                for (FeatureStructure fs : condition.getBodySite().toArray()) {
                    if (!(fs instanceof CodeableConcept)) {
                        continue;
                    }
                    conceptInstances.add(copyCodeableConcept((CodeableConcept) fs, "Condition.bodySite",
                            JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID()));
                }
                setSlotInstanceArr(conditionInstance, "has_Condition.bodySite", conceptInstances);
            }
            // - Evidence
            if (condition.getEvidence() != null && condition.getEvidence().size() > 0) {
                LinkedList<SimpleInstance> conceptInstances = new LinkedList<>();
                for (FeatureStructure fs : condition.getEvidence().toArray()) {
                    if (!(fs instanceof CodeableConcept)) {
                        continue;
                    }
                    conceptInstances.add(copyCodeableConcept((CodeableConcept) fs, "Condition.evidence.code",
                            JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID()));
                }
                setSlotInstanceReference(conditionInstance, "has_Condition.evidence.code", conceptInstances.getFirst());
            }
            // - Severity
            if (condition.getSeverity() != null) {
                setSlotInstanceReference(conditionInstance, "has_Condition.severity",
                        copyCodeableConcept(condition.getSeverity(), "Condition.severity",
                                JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID())
                );
            }
            // - Negation stored as abatement TODO
            if (condition.getAbatementString() != null) {
                setSlotInstanceReference(conditionInstance, "has_Condition.abatementString",
                        createProperty("Condition.abatementString",
                                condition.getAbatementString().getValue(),
                                JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID(),
                                condition.getBegin(),
                                condition.getEnd(),
                                condition.getAbatementString().getValue()));
            }
        }
    }

    private void copyProcedures(JCas cas) throws TextSourceAccessException {
        for (Procedure procedure : JCasUtil.select(cas, Procedure.class)) {
            SimpleInstance procedureInstance = annotationUtil.createAnnotation(
                    getClass("Procedure"),
                    Collections.singletonList(new Span(procedure.getBegin(), procedure.getEnd())),
                    procedure.getCoveredText(),
                    JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID());
            // - Procedure Code
            if (procedure.getCode() != null) {
                setSlotInstanceReference(procedureInstance,
                        "has_Procedure.code",
                        copyCodeableConcept(
                                procedure.getCode(),
                                "Procedure.code",
                                JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID()
                        ));
            }
            // - Body Site
            if (procedure.getBodySite() != null && procedure.getBodySite().size() > 0) {
                LinkedList<Object> conceptInstances = new LinkedList<>();
                for (FeatureStructure fs : procedure.getBodySite().toArray()) {
                    if (!(fs instanceof CodeableConcept)) {
                        continue;
                    }
                    conceptInstances.add(copyCodeableConcept((CodeableConcept) fs, "Procedure.bodySite",
                            JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID()));
                }
                setSlotInstanceArr(procedureInstance, "has_Procedure.bodySite", conceptInstances);
            }
        }
    }

    private void copyMedicationStatements(JCas cas) throws TextSourceAccessException {
        String docID = JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID(); //TODO
    }


    private void copyFamilyMemberHistory(JCas cas) throws TextSourceAccessException {
        for (FamilyMemberHistory fmh : JCasUtil.select(cas, FamilyMemberHistory.class)) {
            SimpleInstance procedureInstance = annotationUtil.createAnnotation(
                    getClass("FamilyMemberHistory"),
                    Collections.singletonList(new Span(fmh.getBegin(), fmh.getEnd())),
                    fmh.getCoveredText(),
                    JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID());
            // - Procedure Code
            if (fmh.getRelationship() != null) {
                setSlotInstanceReference(procedureInstance,
                        "has_FamilyMemberHistory.relationship",
                        copyCodeableConcept(
                                fmh.getRelationship(),
                                "FamilyMemberHistory.relationship",
                                JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID()
                        ));
            }
        }
    }

    private SimpleInstance copyCodeableConcept(CodeableConcept concept, String path, String documentID) throws TextSourceAccessException {
        int begin = concept.getBegin();
        int end = concept.getEnd();
        String covered = concept.getCoveredText();
        SimpleInstance codeableConceptInstance = annotationUtil.createAnnotation(
                getClass(path),
                Collections.singletonList(new Span(concept.getBegin(), concept.getEnd())),
                concept.getCoveredText(),
                documentID
        );
        if (concept.getText() != null) {
            setSlotInstanceReference(codeableConceptInstance, "has_CodeableConcept.text", createProperty("CodeableConcept.text", concept.getText().getValue(), documentID, begin, end, covered));
        }
        if (concept.getCoding() != null && concept.getCoding().size() > 0) {
            LinkedList<Object> codingInstances = new LinkedList<>();
            for (FeatureStructure fs2 : concept.getCoding().toArray()) {
                if (!(fs2 instanceof Coding)) {
                    continue;
                }
                Coding coding = (Coding) fs2;
                SimpleInstance codingInstance = annotationUtil.createAnnotation(
                        getClass("CodeableConcept.coding"),
                        Collections.singletonList(new Span(coding.getBegin(), coding.getEnd())),
                        coding.getCoveredText(),
                        documentID
                );
                if (coding.getCode() != null) {
                    setSlotInstanceReference(codingInstance, "has_Coding.code", createProperty("Coding.code", coding.getCode().getValue(), documentID, begin, end, coding.getCode().getValue()));
                }
                if (coding.getDisplay() != null) {
                    setSlotInstanceReference(codingInstance, "has_Coding.display", createProperty("Coding.display", coding.getDisplay().getValue(), documentID, begin, end, coding.getDisplay().getValue()));
                }
                if (coding.getSystem() != null) {
                    setSlotInstanceReference(codingInstance, "has_Coding.system", createProperty("Coding.system", coding.getSystem().getValue(), documentID, begin, end, coding.getSystem().getValue()));
                }
                if (coding.getUserSelected() != null) {
                    setSlotInstanceReference(codingInstance, "has_Coding.userSelected", createProperty("Coding.userSelected", coding.getUserSelected().getValue(), documentID, begin, end, coding.getUserSelected().getValue()));
                }
                if (coding.getVersion() != null) {
                    setSlotInstanceReference(codingInstance, "has_Coding.version", createProperty("Coding.version", coding.getVersion().getValue(), documentID, begin, end, coding.getVersion().getValue()));
                }
                codingInstances.add(codingInstance);
            }
            setSlotInstanceArr(codeableConceptInstance, "has_CodeableConcept.coding", codingInstances);
        }
        return codeableConceptInstance;
    }

    private SimpleInstance createProperty(String name, String value, String docID, int begin, int end, String displayName) throws TextSourceAccessException {
        SimpleInstance propertyInstance = annotationUtil.createAnnotation(getClass(name), Collections.singletonList(new Span(begin, end)), displayName, docID);
        setSlotInstanceValue(propertyInstance, "normalized_value", value);
        return propertyInstance;
    }


    private void setSlotInstanceValue(SimpleInstance parent, String slotName, String slotRef) {
        Slot slot = knowtator.getKnowledgeBase().getSlot(slotName);
        SimpleInstance slotInstance = mentionUtil.getMentionInstance(parent);
        if (slotInstance == null) {
            slotInstance = mentionUtil.createMention(slot);
        }
        if (!(mentionUtil.isInstanceMention(parent) || mentionUtil.isClassMention(parent))) {
            parent = annotationUtil.getMention(parent);
        }
        mentionUtil.setSlotMentionValues(slotInstance, Collections.singletonList(slotRef));
        mentionUtil.addSlotMention(parent, slotInstance);
        mentionUtil.addInverse(parent, slot, slotInstance);
        annotationUtil.setMention(parent, slotInstance);
    }

    private void setSlotInstanceReference(SimpleInstance parent, String slotName, SimpleInstance slotRef) {
        Slot slot = knowtator.getKnowledgeBase().getSlot(slotName);
        SimpleInstance slotInstance = mentionUtil.getMentionInstance(parent);
        if (slotInstance == null) {
            slotInstance = mentionUtil.createMention(slot);
        }
        if (!(mentionUtil.isInstanceMention(parent) || mentionUtil.isClassMention(parent))) {
            parent = annotationUtil.getMention(parent);
        }
        if (!mentionUtil.isClassMention(slotRef)) {
            slotRef = annotationUtil.getMention(slotRef);
        }
        mentionUtil.setSlotMentionValues(slotInstance, Collections.singletonList(slotRef));
        mentionUtil.addSlotMention(parent, slotInstance);
        mentionUtil.addInverse(parent, slot, slotInstance);
        annotationUtil.setMention(parent, slotInstance);
    }

    private void setSlotInstanceArr(SimpleInstance parent, String slotName, List<Object> slotRef) {
        Slot slot = knowtator.getKnowledgeBase().getSlot(slotName);
        SimpleInstance slotInstance = mentionUtil.getMentionInstance(parent);
        List<Object> add = new LinkedList<>(); // Reconstruct and check for class mention
        for (Object o : slotRef) {
            if (o instanceof SimpleInstance) {
                SimpleInstance si = (SimpleInstance) o;
                if (!mentionUtil.isClassMention(si)) {
                    o = annotationUtil.getMention(si);
                }
            }
            add.add(o);
        }
        if (slotInstance == null) {
            slotInstance = mentionUtil.createMention(slot);
        }
        if (!(mentionUtil.isInstanceMention(parent) || mentionUtil.isClassMention(parent))) {
            parent = annotationUtil.getMention(parent);
        }
        mentionUtil.setSlotMentionValues(slotInstance, add);
        mentionUtil.addSlotMention(parent, slotInstance);
        mentionUtil.addInverse(parent, slot, slotInstance);
        annotationUtil.setMention(parent, slotInstance);
    }

    private Cls getClass(String name) {
        return knowtator.getKnowledgeBase().getCls(name);
    }

    private void save() {
        boolean success = false;
        while (!success) { // Spin until successful save: stopgap measure due to occasional access/write lock issues
            try {
                ProjectUtil.saveProject(project);
                success = true;
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void collectionProcessComplete() {
        save();
    }
}
