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
import org.hl7.fhir.dstu3.model.StringType;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ConditionResourceProducer implements ResourceProducer<Condition> {

    @Override
    public List<Condition> produce(JCas cas) {
        List<Condition> ret = new LinkedList<>();
        String documentID = JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID();
        for (org.hl7.fhir.Condition condition : JCasUtil.select(cas, org.hl7.fhir.Condition.class)) {
            ret.add(produce(documentID, condition));
        }
        return ret;
    }

    @Override
    public Condition produce(String documentID, Annotation ann) {
        if (!(ann instanceof org.hl7.fhir.Condition)) {
            throw new IllegalArgumentException("Expected condition, got a " + ann.getClass());
        }
        org.hl7.fhir.Condition condition = (org.hl7.fhir.Condition) ann;
        Condition out = new Condition();
        out.setIdentifier(Collections.singletonList(Cas2FHIRUtils.generateUIDIdentifer(Cas2FHIRUtils.generateUIDforAnnotation(documentID, condition))));
        out.setId("Condition/" + Cas2FHIRUtils.generateUIDforAnnotation(documentID, condition));
        // Code
        if (condition.getCode() != null) {
            out.setCode(Cas2FHIRUtils.codeableConceptFromCAS(condition.getCode()));
        }
        // Body Site
        if (condition.getBodySite() != null && condition.getBodySite().size() > 0) {
            LinkedList<CodeableConcept> sites = new LinkedList<>();
            for (FeatureStructure fs : condition.getBodySite().toArray()) {
                sites.add(Cas2FHIRUtils.codeableConceptFromCAS((org.hl7.fhir.CodeableConcept) fs));
            }
            out.setBodySite(sites);
        }
        // Evidence
        if (condition.getEvidence() != null && condition.getEvidence().size() > 0) {
            LinkedList<Condition.ConditionEvidenceComponent> evidence = new LinkedList<>();
            for (FeatureStructure fs : condition.getEvidence().toArray()) {
                Condition.ConditionEvidenceComponent evidenceComponent = new Condition.ConditionEvidenceComponent();
                evidenceComponent.setCode(Cas2FHIRUtils.codeableConceptFromCAS((org.hl7.fhir.CodeableConcept) fs));
                evidence.add(evidenceComponent);
            }
            out.setEvidence(evidence);
        }
        // Degree of
        if (condition.getSeverity() != null) {
            out.setSeverity(Cas2FHIRUtils.codeableConceptFromCAS(condition.getSeverity()));
        }
        // Negation via abatement
        if (condition.getAbatementString() != null) {
            out.setAbatement(new StringType().setValue(condition.getAbatementString().getValue()));
        }
        return out;
    }
}
