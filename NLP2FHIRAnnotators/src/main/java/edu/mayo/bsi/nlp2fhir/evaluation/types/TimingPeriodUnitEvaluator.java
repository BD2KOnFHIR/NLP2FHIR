package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.TimingRepeat;

import java.io.File;

public class TimingPeriodUnitEvaluator extends EvaluationTask<TimingRepeat> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public TimingPeriodUnitEvaluator(File evalDir) {
        super("Dosage.Timing.repeat.period%2Bunit", evalDir);
    }

    @Override
    protected boolean matches(TimingRepeat ann, KnowtatorAnnotation gold) {
        return hasInterestedProperty(ann)
                && gold.getStandardText().split(",").length == 2
                && ann.getPeriodUnit().getValue().equalsIgnoreCase(gold.getStandardText().split(",")[1]);
    }

    @Override
    protected boolean hasInterestedProperty(TimingRepeat ann) {
        return ann.getPeriodUnit() != null;
    }

    @Override
    public Class<TimingRepeat> getAnnotationClass() {
        return TimingRepeat.class;
    }

    @Override
    public String getSuffix() {
        return "units";
    }
}
