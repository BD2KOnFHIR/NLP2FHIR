package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.DosageInstruction;

import java.io.File;

public class DosageRouteEvaluator extends EvaluationTask<DosageInstruction> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageRouteEvaluator(File evalDir) {
        super("Dosage.route", evalDir);
    }

    @Override
    protected boolean matches(DosageInstruction ann, KnowtatorAnnotation gold) {
        return ann.getRoute().getText().getValue().equalsIgnoreCase(gold.getStandardText());
    }

    @Override
    protected boolean hasInterestedProperty(DosageInstruction ann) {
        return ann.getRoute() != null;
    }

    @Override
    public Class<DosageInstruction> getAnnotationClass() {
        return DosageInstruction.class;
    }
}
