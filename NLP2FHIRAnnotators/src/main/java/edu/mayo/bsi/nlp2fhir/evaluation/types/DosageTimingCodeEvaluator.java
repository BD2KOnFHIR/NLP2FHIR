package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.CodeableConcept;

import java.io.File;

public class DosageTimingCodeEvaluator extends EvaluationTask<CodeableConcept> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageTimingCodeEvaluator(File evalDir) {
        super("Dosage.Timing.code", evalDir);
    }

    @Override
    protected boolean matches(CodeableConcept ann, KnowtatorAnnotation gold) {
        return true; // Lazy match - If overlapping and both are codeable concepts then it is a correct match
    }

    @Override
    protected boolean hasInterestedProperty(CodeableConcept ann) {
        return ann.getCoding() != null && ann.getCoding(0).getSystem().getValue().equalsIgnoreCase("http://hl7.org/fhir/ValueSet/timing-abbreviation");
    }

    @Override
    public Class<CodeableConcept> getAnnotationClass() {
        return CodeableConcept.class;
    }
}
