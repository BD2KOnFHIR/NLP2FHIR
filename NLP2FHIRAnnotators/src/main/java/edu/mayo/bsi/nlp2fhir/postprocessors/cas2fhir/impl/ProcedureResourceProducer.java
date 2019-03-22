package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl;

import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.Cas2FHIRUtils;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducer;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.dstu3.model.Procedure;
import org.hl7.fhir.dstu3.model.Resource;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ProcedureResourceProducer implements ResourceProducer<Procedure> {
    @Override
    public List<Procedure> produce(JCas cas) {
        List<Procedure> ret = new LinkedList<>();
        String documentID = JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID();
        for (org.hl7.fhir.Procedure procedure : JCasUtil.select(cas, org.hl7.fhir.Procedure.class)) {
            ret.add(produce(documentID, procedure));
        }
        return ret;
    }

    @Override
    public Procedure produce(String documentID, Annotation ann) {
        if (!(ann instanceof org.hl7.fhir.Procedure)) {
            throw new IllegalArgumentException("Expected procedure, got " + ann.getClass());
        }
        org.hl7.fhir.Procedure procedure = (org.hl7.fhir.Procedure) ann;
        Procedure out = new Procedure();
        out.setIdentifier(Collections.singletonList(Cas2FHIRUtils.generateUIDIdentifer(Cas2FHIRUtils.generateUIDforAnnotation(documentID, procedure))));
        out.setId("Procedure/" + Cas2FHIRUtils.generateUIDforAnnotation(documentID, procedure));
        // Code
        if (procedure.getCode() != null) {
            out.setCode(Cas2FHIRUtils.codeableConceptFromCAS(procedure.getCode()));
        }
        return out;
    }
}
