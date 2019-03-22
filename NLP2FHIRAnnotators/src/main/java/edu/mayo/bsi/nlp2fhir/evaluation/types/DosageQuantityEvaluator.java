package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.DosageInstruction;

import java.io.File;

public class DosageQuantityEvaluator extends EvaluationTask<DosageInstruction> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageQuantityEvaluator(File evalDir) {
        super("Dosage.dose.quantity.value", evalDir);
    }

    @Override
    protected boolean matches(DosageInstruction ann, KnowtatorAnnotation gold) {
        if (!hasInterestedProperty(ann)) {
            return false;
        }
        String extractedVal = ann.getDoseSimpleQuantity().getValue().getValue();
        String goldVal = gold.getStandardText();
        goldVal = Util.normalizeNumber(goldVal); // TODO gold standard annotation is not normalized, so we normalize here
        try {
            double extractedDouble = Double.valueOf(extractedVal);
            double goldDouble = Double.valueOf(goldVal);
            return extractedDouble == goldDouble;
        } catch (NumberFormatException e) {
            return extractedVal.equalsIgnoreCase(goldVal);
        }
    }

    @Override
    protected boolean hasInterestedProperty(DosageInstruction ann) {
        return ann.getDoseSimpleQuantity() != null;
    }

    @Override
    public Class<DosageInstruction> getAnnotationClass() {
        return DosageInstruction.class;
    }
}
