package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.Ratio;

import java.io.File;

// TODO: reimplement this using something other than ratio as it can also be used elsewhere
public class IngredientDenominatorValueEvaluator extends EvaluationTask<Ratio> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public IngredientDenominatorValueEvaluator(File evalDir) {
        super("Medication.ingredient.amount.denumerator.quantity.value", evalDir);
    }

    @Override
    protected boolean matches(Ratio ann, KnowtatorAnnotation gold) {
        return hasInterestedProperty(ann) && ann.getDenominator().getValue().getValue().equalsIgnoreCase(gold.getStandardText());
    }

    @Override
    protected boolean hasInterestedProperty(Ratio ann) {
        return ann.getDenominator() != null;
    }

    @Override
    public Class<Ratio> getAnnotationClass() {
        return Ratio.class;
    }
}
