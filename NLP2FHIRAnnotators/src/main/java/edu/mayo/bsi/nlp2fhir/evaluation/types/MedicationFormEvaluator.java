package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.MedicationProduct;

import java.io.File;

public class MedicationFormEvaluator extends EvaluationTask<MedicationProduct> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public MedicationFormEvaluator(File evalDir) {
        super("Medication.form", evalDir);
    }

    @Override
    protected boolean matches(MedicationProduct ann, KnowtatorAnnotation gold) {
        return ann.getForm().getText().getValue().equalsIgnoreCase(gold.getStandardText());
    }

    @Override
    protected boolean hasInterestedProperty(MedicationProduct ann) {
        return ann.getForm() != null;
    }

    @Override
    public Class<MedicationProduct> getAnnotationClass() {
        return MedicationProduct.class;
    }
}
