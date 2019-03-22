package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.TimingRepeat;

import java.io.File;

// TODO gold standard duration values are not standardized
public class TimingDurationValueEvaluator extends EvaluationTask<TimingRepeat> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public TimingDurationValueEvaluator(File evalDir) {
        super("Dosage.Timing.repeat.duration", evalDir);
    }

    @Override
    protected boolean matches(TimingRepeat ann, KnowtatorAnnotation gold) {
        if (!hasInterestedProperty(ann)) {
            return false;
        }
        if (ann.getDuration() != null) {
            String extractedVal = ann.getDuration().getValue();
            String goldVal = gold.getStandardText();
            try {
                double extractedDouble = Double.valueOf(extractedVal);
                double goldDouble = Double.valueOf(Util.normalizeNumber(goldVal)); // TODO evaluation-side normalization of gold standard
                return extractedDouble == goldDouble;
            } catch (Exception e) {
                return extractedVal.equalsIgnoreCase(goldVal);
            }
        } else {
            String extractedVal = ann.getBoundsDuration().getValue().getValue();
            String goldVal = gold.getStandardText();
            try {
                double extractedDouble = Double.valueOf(extractedVal);
                double goldDouble = Double.valueOf(Util.normalizeNumber(goldVal));// TODO evaluation-side normalization of gold standard
                return extractedDouble == goldDouble;
            } catch (Exception e) {
                return extractedVal.equalsIgnoreCase(goldVal);
            }
        }
    }

    @Override
    protected boolean hasInterestedProperty(TimingRepeat ann) {
        return ann.getDuration() != null || ann.getBoundsDuration() != null;
    }

    @Override
    public Class<TimingRepeat> getAnnotationClass() {
        return TimingRepeat.class;
    }
}
