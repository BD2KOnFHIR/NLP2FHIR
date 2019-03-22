package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl;

import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.Cas2FHIRUtils;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducer;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.FamilyMemberHistoryCondition;
import org.hl7.fhir.dstu3.model.FamilyMemberHistory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class FamilyMemberHistoryResourceProducer implements ResourceProducer<FamilyMemberHistory> {
    @Override
    public List<FamilyMemberHistory> produce(JCas cas) {
        List<FamilyMemberHistory> ret = new LinkedList<>();
        String documentID = JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID();
        for (org.hl7.fhir.Procedure procedure : JCasUtil.select(cas, org.hl7.fhir.Procedure.class)) {
            ret.add(produce(documentID, procedure));
        }
        return ret;
    }

    @Override
    public FamilyMemberHistory produce(String documentID, Annotation ann) {
        if (!(ann instanceof org.hl7.fhir.FamilyMemberHistory)) {
            throw new IllegalArgumentException("Expected family member history, got " + ann.getClass());
        }
        org.hl7.fhir.FamilyMemberHistory fmh = (org.hl7.fhir.FamilyMemberHistory) ann;
        FamilyMemberHistory out = new FamilyMemberHistory();
        out.setIdentifier(Collections.singletonList(Cas2FHIRUtils.generateUIDIdentifer(Cas2FHIRUtils.generateUIDforAnnotation(documentID, fmh))));
        out.setId("FamilyMemberHistory/" + Cas2FHIRUtils.generateUIDforAnnotation(documentID, fmh));
        // Code
        if (fmh.getCondition() != null && fmh.getCondition().size() > 0) {
            LinkedList<FamilyMemberHistory.FamilyMemberHistoryConditionComponent> conditions = new LinkedList<>();
            for (FeatureStructure fs : fmh.getCondition().toArray()) {
                FamilyMemberHistoryCondition cond = ((FamilyMemberHistoryCondition)fs);
                FamilyMemberHistory.FamilyMemberHistoryConditionComponent comp = new FamilyMemberHistory.FamilyMemberHistoryConditionComponent();
                comp.setCode(Cas2FHIRUtils.codeableConceptFromCAS(cond.getCode()));
                comp.setNote(new org.hl7.fhir.dstu3.model.Annotation().setText(cond.getNote().getText().getValue()));
                conditions.add(comp);
            }
            out.setCondition(conditions);
        }
        return out;
    }
}
