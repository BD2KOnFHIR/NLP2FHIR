package edu.mayo.bsi.nlp2fhir.evaluation.types;

import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.evaluation.api.EvaluationTask;
import org.hl7.fhir.EventTiming;

import java.io.File;

public class DosageWhenEvaluator extends EvaluationTask<EventTiming> {
    /**
     * @param evalDir The temporary evaluation directory
     */
    public DosageWhenEvaluator(File evalDir) {
        super("Dosage.Timing.repeat.when", evalDir);
    }

    @Override
    protected boolean matches(EventTiming ann, KnowtatorAnnotation gold) {
        if (hasInterestedProperty(ann)) {
            String code = Util.getHL7EventTimingCode(0, gold.getStandardText());
            if (code.equals("")) {
                code = gold.getStandardText();
            }
            return ann.getValue().equalsIgnoreCase(code);
        }
        return false;
    }

    @Override
    protected boolean hasInterestedProperty(EventTiming ann) {
        return ann.getValue() != null;
    }

    @Override
    public Class<EventTiming> getAnnotationClass() {
        return EventTiming.class;
    }
}
