package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.apache.uima.cas.FeatureStructure;
import org.hl7.fhir.DosageInstruction;

import java.io.File;

public class DosageAdditionalInstructionsEvaluator extends EvaluationTask<DosageInstruction> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageAdditionalInstructionsEvaluator(File evalDir) {
        super("Dosage.additionalInstructions", evalDir);
    }

    @Override
    protected boolean matches(DosageInstruction ann, KnowtatorAnnotation gold) {
        return true; // No normalized form, go by positional match (which was already prior to method call)
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    @Override
    protected boolean hasInterestedProperty(DosageInstruction ann) {
        if (ann.getAdditionalInstructions() == null) {
            return false;
        }
        for (FeatureStructure ignored : ann.getAdditionalInstructions().toArray()) { // TODO double check math on this, maybe use CodeableConcept instead
            return true; // Has at least one additional instruction
        }
        return false;
    }

    @Override
    public Class<DosageInstruction> getAnnotationClass() {
        return DosageInstruction.class;
    }
}
