package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.Ratio;

import java.io.File;

// TODO don't use ratio
public class IngredientNumeratorUnitEvaluator extends EvaluationTask<Ratio> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public IngredientNumeratorUnitEvaluator(File evalDir) {
        super("Medication.ingredient.amount.numerator.quantity.unit", evalDir);
    }

    @Override
    protected boolean matches(Ratio ann, KnowtatorAnnotation gold) {
        return hasInterestedProperty(ann) && ann.getNumerator().getUnit().getValue().equalsIgnoreCase(gold.getStandardText());
    }

    @Override
    protected boolean hasInterestedProperty(Ratio ann) {
        return ann.getNumerator() != null && ann.getNumerator().getUnit() != null;
    }

    @Override
    public Class<Ratio> getAnnotationClass() {
        return Ratio.class;
    }
}
