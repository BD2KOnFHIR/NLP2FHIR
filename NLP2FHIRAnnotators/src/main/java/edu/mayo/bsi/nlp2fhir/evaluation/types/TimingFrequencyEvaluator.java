package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.TimingRepeat;

import java.io.File;

public class TimingFrequencyEvaluator extends EvaluationTask<TimingRepeat> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public TimingFrequencyEvaluator(File evalDir) {
        super("Dosage.Timing.repeat.frequency", evalDir);
    }

    @Override
    protected boolean matches(TimingRepeat ann, KnowtatorAnnotation gold) {
        if (!hasInterestedProperty(ann)) {
            return false;
        }
        String extractedVal = ann.getFrequency().getValue();
        String goldVal = gold.getStandardText();
        try {
            double extractedDouble = Double.valueOf(extractedVal);
            double goldDouble = Double.valueOf(Util.normalizeNumber(goldVal)); //TODO evaluation side normalization of gold standard
            return extractedDouble == goldDouble;
        } catch (Exception e) {
            return extractedVal.equalsIgnoreCase(goldVal);
        }
    }

    @Override
    protected boolean hasInterestedProperty(TimingRepeat ann) {
        return ann.getFrequency() != null;
    }

    @Override
    public Class<TimingRepeat> getAnnotationClass() {
        return TimingRepeat.class;
    }
}
