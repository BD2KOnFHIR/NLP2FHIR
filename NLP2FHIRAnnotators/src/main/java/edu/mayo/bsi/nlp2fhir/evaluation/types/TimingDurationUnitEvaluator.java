package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.TimingRepeat;

import java.io.File;

public class TimingDurationUnitEvaluator extends EvaluationTask<TimingRepeat> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public TimingDurationUnitEvaluator(File evalDir) {
        super("Dosage.Timing.repeat.durationUnit", evalDir);
    }

    @Override
    protected boolean matches(TimingRepeat ann, KnowtatorAnnotation gold) {
        if (ann.getDuration() != null) {
            return ann.getDurationUnit().getValue().equalsIgnoreCase(Util.transformUnitOfTime(gold.getStandardText())); // TODO evaluation-side standardization
        } else
            return ann.getBoundsDuration() != null && ann.getBoundsDuration().getUnit() != null && ann.getBoundsDuration().getUnit().getValue().equalsIgnoreCase(Util.transformUnitOfTime(gold.getStandardText())); // TODO evaluation-side standardization
    }

    @Override
    protected boolean hasInterestedProperty(TimingRepeat ann) {
        return ann.getDurationUnit() != null || (ann.getBoundsDuration() != null && ann.getBoundsDuration().getUnit() != null);
    }

    @Override
    public Class<TimingRepeat> getAnnotationClass() {
        return TimingRepeat.class;
    }
}
