package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir;

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.Ratio;
import org.hl7.fhir.Resource;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Identifier;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class Cas2FHIRUtils {
    public static UUID generateUIDforAnnotation(String docID, Annotation resource) {
        return UUID.nameUUIDFromBytes((docID.toLowerCase() + "::" + resource.getBegin() + ":" + resource.getEnd() + ":" + resource.getCoveredText() + ":" + resource.getClass().getName()).getBytes());
    }
    public static Identifier generateUIDIdentifer(UUID uid) {
        return new Identifier()
                .setSystem("urn:ietf:rfc:3986")
                .setValue("urn:uuid:" + uid.toString().toLowerCase());
    }

    public static CodeableConcept codeableConceptFromCAS(org.hl7.fhir.CodeableConcept sourceConcept) {
        CodeableConcept ret = new CodeableConcept();
        if (sourceConcept.getText() != null) {
            ret.setText(sourceConcept.getText().getValue());
        }
        // - Codes
        if (sourceConcept.getCoding() != null) {
            List<Coding> outCodings = new LinkedList<>();
            for (FeatureStructure fs : sourceConcept.getCoding().toArray()) {
                org.hl7.fhir.Coding c = (org.hl7.fhir.Coding) fs;
                Coding outCoding = new Coding();
                outCoding.setCode(c.getCode().getValue());
                outCoding.setSystem(c.getSystem().getValue());
                outCodings.add(outCoding);
            }
            ret.setCoding(outCodings);
        }
        return ret;
    }

    public static void ratioDenominatorCopyFromCASRatio(Ratio r, org.hl7.fhir.dstu3.model.Ratio rOut) {
        if (r.getDenominator() != null) {
            org.hl7.fhir.dstu3.model.Quantity qOut = new org.hl7.fhir.dstu3.model.Quantity();
            qOut.setValue(Double.valueOf(r.getDenominator().getValue().getValue()));
            if (r.getDenominator().getUnit() != null) {
                qOut.setUnit(r.getDenominator().getUnit().getValue());
            }
            rOut.setDenominator(qOut);
        }
    }
}
