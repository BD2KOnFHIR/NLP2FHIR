package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.CodeableConcept;

import java.io.File;

public class DosageAsNeededCodeableConceptEvaluator extends EvaluationTask<CodeableConcept> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageAsNeededCodeableConceptEvaluator(File evalDir) {
        super("Dosage.asNeededCodeableConcept", evalDir);
    }

    @Override
    protected boolean matches(CodeableConcept ann, KnowtatorAnnotation gold) {
        return true; // Lazy match - If overlapping and both are codeable concepts then it is a correct match
    }

    @Override
    protected boolean hasInterestedProperty(CodeableConcept ann) {
        return ann.getCoding() != null && ann.getCoding(0).getSystem().getValue().equalsIgnoreCase("https://www.hl7.org/fhir/ValueSet/condition-code");
    }

    @Override
    public Class<CodeableConcept> getAnnotationClass() {
        return CodeableConcept.class;
    }
}
