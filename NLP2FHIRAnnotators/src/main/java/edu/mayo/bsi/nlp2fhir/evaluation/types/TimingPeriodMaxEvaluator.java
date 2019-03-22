package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.TimingRepeat;

import java.io.File;

public class TimingPeriodMaxEvaluator extends EvaluationTask<TimingRepeat> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public TimingPeriodMaxEvaluator(File evalDir) {
        super("Dosage.Timing.repeat.periodMax", evalDir);
    }

    @Override
    protected boolean matches(TimingRepeat ann, KnowtatorAnnotation gold) {
        if (!hasInterestedProperty(ann)) {
            return false;
        }
        String extractedVal = ann.getPeriodMax().getValue();
        String goldVal = gold.getStandardText();
        try {
            double extractedDouble = Double.valueOf(extractedVal);
            goldVal = Util.normalizeNumber(goldVal); // TODO gold standard annotation is not normalized, so we normalize here
            double goldDouble = Double.valueOf(goldVal);
            return extractedDouble == goldDouble;
        } catch (Exception e) {
            return extractedVal.equalsIgnoreCase(goldVal);
        }
    }

    @Override
    protected boolean hasInterestedProperty(TimingRepeat ann) {
        return ann.getPeriodMax() != null;
    }

    @Override
    public Class<TimingRepeat> getAnnotationClass() {
        return TimingRepeat.class;
    }

    @Override
    protected String getSuffix() {
        return "value";
    }
}
