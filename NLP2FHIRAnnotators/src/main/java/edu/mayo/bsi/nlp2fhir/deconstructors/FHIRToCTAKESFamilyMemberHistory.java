package edu.mayo.bsi.nlp2fhir.deconstructors;

import edu.mayo.bsi.nlp2fhir.nlp.GenericRelation;
import org.apache.ctakes.typesystem.type.textsem.DiseaseDisorderMention;
import org.apache.ctakes.typesystem.type.textsem.Modifier;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.hl7.fhir.FamilyMemberHistory;

import java.util.List;

/**
 * Produces relationship relations from FHIR FamilyMemberHistory annotations for training
 */
public class FHIRToCTAKESFamilyMemberHistory extends JCasAnnotator_ImplBase {

    private static String RELATIONSHIP_TYPE =  "FMH_RELATIONSHIP";

    @Override
    public void process(JCas cas) {
        for (FamilyMemberHistory history : JCasUtil.select(cas, FamilyMemberHistory.class)) {
            if (history.getRelationship() != null) {
                List<Modifier> subjects = JCasUtil.selectAt(cas, Modifier.class, history.getRelationship().getBegin(), history.getRelationship().getEnd());
                if (subjects.size() == 0) {
                    continue;
                }
                List<DiseaseDisorderMention> mentions = JCasUtil.selectCovered(cas, DiseaseDisorderMention.class, history.getBegin(), history.getEnd());
                if (mentions.size() == 0) {
                    continue;
                }
                GenericRelation rel = new GenericRelation(cas);
                rel.setArg1(mentions.iterator().next());
                rel.setArg2(subjects.iterator().next());
                rel.setRelationType(RELATIONSHIP_TYPE);
                rel.addToIndexes();
            }
        }
    }
}
