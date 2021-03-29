package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl;

import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.Cas2FHIRUtils;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducer;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Observation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ObservationResourceProducer implements ResourceProducer<Observation> {
    @Override
    public List<Observation> produce(JCas cas) {
        List<Observation> ret = new ArrayList<>();
        String documentID = JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID();
        for (org.hl7.fhir.Observation condition : JCasUtil.select(cas, org.hl7.fhir.Observation.class)) {
            ret.add(produce(documentID, condition));
        }
        return ret;
    }

    @Override
    public Observation produce(String documentID, Annotation ann) {
        if (!(ann instanceof org.hl7.fhir.Observation)) {
            throw new IllegalArgumentException("Expected Observation, got a " + ann.getClass());
        }
        org.hl7.fhir.Observation obs = (org.hl7.fhir.Observation) ann;
        Observation out = new Observation();
        out.setIdentifier(Collections.singletonList(Cas2FHIRUtils.generateUIDIdentifer(Cas2FHIRUtils.generateUIDforAnnotation(documentID, obs))));
        out.setId("Observation/" + Cas2FHIRUtils.generateUIDforAnnotation(documentID, obs));
        // Code
        if (obs.getCode() != null) {
            out.setCode(Cas2FHIRUtils.codeableConceptFromCAS(obs.getCode()));
        }
        return out;
    }
}
