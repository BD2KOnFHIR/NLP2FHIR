package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.MedicationStatement;

import java.io.File;

public class MedicationCodeableConceptEvaluator extends EvaluationTask<MedicationStatement> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public MedicationCodeableConceptEvaluator(File evalDir) {
        super("MedicationStatement.medicationCodeableConcept", evalDir);
    }

    @Override
    protected boolean matches(MedicationStatement ann, KnowtatorAnnotation gold) {
        return true; // Lazy match - normalized form is not saved in gold standard annotation; if both are of same type in same place assume matches
    }

    @Override
    protected boolean hasInterestedProperty(MedicationStatement ann) {
        return true; // A MedicationStatement will always have a codeable concept
    }

    @Override
    public Class<MedicationStatement> getAnnotationClass() {
        return MedicationStatement.class;
    }
}
