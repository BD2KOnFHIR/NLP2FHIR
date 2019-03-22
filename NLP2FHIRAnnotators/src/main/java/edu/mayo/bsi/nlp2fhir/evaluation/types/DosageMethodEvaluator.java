package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.CodeableConcept;

import java.io.File;

public class DosageMethodEvaluator extends EvaluationTask<CodeableConcept> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageMethodEvaluator(File evalDir) {
        super("Dosage.method", evalDir);
    }

    @Override
    protected boolean matches(CodeableConcept ann, KnowtatorAnnotation gold) {
        return true; // Is a concept that matches with the correct defining URI
    }

    @Override
    protected boolean hasInterestedProperty(CodeableConcept ann) {
        return ann.getCoding() != null && ann.getCoding(0).getSystem().getValue().equalsIgnoreCase("http://hl7.org/fhir/ValueSet/administration-method-codes");
    }

    @Override
    public Class<CodeableConcept> getAnnotationClass() {
        return CodeableConcept.class;
    }
}
