package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.DosageInstruction;

import java.io.File;

public class DosageQuantityUnitEvaluator extends EvaluationTask<DosageInstruction> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageQuantityUnitEvaluator(File evalDir) {
        super("Dosage.dose.quantity.unit", evalDir);
    }

    @Override
    protected boolean matches(DosageInstruction ann, KnowtatorAnnotation gold) {
        return hasInterestedProperty(ann) && ann.getDoseSimpleQuantity().getUnit().getValue().trim().equalsIgnoreCase(gold.getStandardText().trim());
    }

    @Override
    protected boolean hasInterestedProperty(DosageInstruction ann) {
        return ann.getDoseSimpleQuantity() != null && ann.getDoseSimpleQuantity().getUnit() != null;
    }

    @Override
    public Class<DosageInstruction> getAnnotationClass() {
        return DosageInstruction.class;
    }
}
