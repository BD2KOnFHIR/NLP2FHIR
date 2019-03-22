package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.DosageInstruction;

import java.io.File;

public class DosageAsNeededBooleanEvaluator extends EvaluationTask<DosageInstruction> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageAsNeededBooleanEvaluator(File evalDir) {
        super("Dosage.asNeededBoolean", evalDir);
    }

    @Override
    protected boolean matches(DosageInstruction ann, KnowtatorAnnotation gold) {
        return true; // If dosage instruction has as needed set, then is automatically true
    }

    @Override
    protected boolean hasInterestedProperty(DosageInstruction ann) {
        return ann.getAsNeededBoolean() != null;
    }

    @Override
    public Class<DosageInstruction> getAnnotationClass() {
        return DosageInstruction.class;
    }
}
