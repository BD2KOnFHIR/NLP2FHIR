package edu.mayo.bsi.nlp2fhir.transformers;

import edu.mayo.bsi.nlp.vts.SNOMEDCT;
import edu.mayo.bsi.nlp.vts.UMLS;
import edu.mayo.bsi.nlp2fhir.nlp.GenericRelation;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.nlp.Section;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.DiseaseDisorderMention;
import org.apache.ctakes.typesystem.type.textsem.Modifier;
import org.apache.ctakes.typesystem.type.textsem.SubjectModifier;
import org.apache.logging.log4j.Level;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.hl7.fhir.*;

import java.lang.Integer;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class CTAKESToFHIRFamilyMemberHistory extends JCasAnnotator_ImplBase {
    public static String RELATIONSHIP_TYPE = "FMH_RELATIONSHIP";
    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
        Set<Span> constructed = new HashSet<>();
        for (Section s : JCasUtil.select(cas, Section.class)) {
            if (s.getId().equalsIgnoreCase("10157-6")
                    || s.getId().equalsIgnoreCase("29762-2")) {
                buildFamilyMemberHistory(cas, s, constructed);
            }
        }
    }

    private void buildFamilyMemberHistory(JCas cas, Section section, Set<Span> constructed) {
        Map<DiseaseDisorderMention, List<Modifier>> rels = new HashMap<>();
        for (GenericRelation rel : JCasUtil.select(cas, GenericRelation.class)) {
            if (rel.getRelationType().equals(RELATIONSHIP_TYPE)) {
                rels.computeIfAbsent((DiseaseDisorderMention) rel.getArg1(), k -> new LinkedList<>()).add((Modifier) rel.getArg2());
            }
        }
        for (DiseaseDisorderMention mention : JCasUtil.selectCovered(cas, DiseaseDisorderMention.class, section)) {
            Span span = new Span(mention.getBegin(), mention.getEnd(), "FMHCondition");
            if (constructed.contains(span)) {
                continue;
            } else {
                constructed.add(span);
            }
            FamilyMemberHistory fmh = new FamilyMemberHistory(cas, mention.getBegin(), mention.getEnd());
            FamilyMemberHistoryCondition condition = new FamilyMemberHistoryCondition(cas, mention.getBegin(), mention.getEnd());
            if (mention.getOntologyConceptArr() != null) {
                CodeableConcept concept = new CodeableConcept(cas, mention.getBegin(), mention.getEnd());
                concept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, cas, mention.getCoveredText(), mention.getBegin(), mention.getEnd()));
                FSArray conceptArr = mention.getOntologyConceptArr();
                for (int j = 0; j < conceptArr.size(); j++) {
                    FeatureStructure fs = conceptArr.get(j);
                    if (!(fs instanceof UmlsConcept)) {
                        throw new AssertionError("Ontology Concept not a UMLS Concept!");
                    }
                    UmlsConcept c = (UmlsConcept) fs;
                    String code = null; // Vocab specific
                    String term = c.getPreferredText();
                    String rootItem = "404684003"; // As defined in https://www.hl7.org/fhir/valueset-condition-code.html
                    try {
                        boolean hasInterestedParent = false;
                        for (String snomedCode : UMLS.getSourceCodesForVocab(UMLS.UMLSSourceVocabulary.SNOMEDCT_US, c.getCui())) {
                            code = snomedCode;
                            hasInterestedParent = SNOMEDCT.isChild(snomedCode, rootItem);
                            if (hasInterestedParent) {
                                Collection<String> preferred = UMLS.getSourceTermPreferredText(UMLS.UMLSSourceVocabulary.SNOMEDCT_US, snomedCode);
                                if (!preferred.isEmpty()) {
                                    term = preferred.iterator().next();
                                }
                                break;
                            }
                        }
                        if (!hasInterestedParent) {
                            continue;
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    if (code != null) {
                        concept.setCoding(new FSArray(cas, 1));
                        Coding coding = new Coding(cas, mention.getBegin(), mention.getEnd());
                        coding.addToIndexes();
                        coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, cas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                        coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, cas, code, mention.getBegin(), mention.getEnd()));
                        coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, cas, term, mention.getBegin(), mention.getEnd()));
                        concept.setCoding(0, coding);
                        break;
                    }
                }
                if (concept.getCoding() == null || concept.getCoding().size() == 0) {
                    continue;
                }
                concept.addToIndexes();
                condition.setCode(concept);
                Util.expand(condition, concept.getBegin(), concept.getEnd());
                Util.expand(fmh, concept.getBegin(), concept.getEnd());
                if (rels.containsKey(mention)) {
                    Modifier target = null;
                    int currDist = Integer.MAX_VALUE;
                    int beg1 = concept.getBegin();
                    int end1 = concept.getEnd();
                    for (Modifier m : rels.get(mention)) {
                        int beg2 = m.getBegin();
                        int end2 = m.getEnd();
                        int dist;
                        if (beg2 > end1) {
                            dist = beg2 - end1;
                        } else {
                            dist = end2 - beg1;
                        }
                        if (dist < currDist) {
                            dist = currDist;
                            target = m;
                        }
                    }
                    if (target != null) {
                        String text = target.getCoveredText();
                        CodeableConcept relConcept = new CodeableConcept(cas, target.getBegin(), target.getEnd());
                        relConcept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, cas, text, target.getBegin(), target.getEnd()));
                        relConcept.addToIndexes();
                        fmh.setRelationship(relConcept);
                    }
                }
                boolean negated = mention.getPolarity() == -1;
                Annotation noteAnn = new Annotation(cas, mention.getBegin(), mention.getEnd());
                noteAnn.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, cas, negated + "", mention.getBegin(), mention.getEnd()));
                noteAnn.addToIndexes();
                condition.setNote(noteAnn);
                // Uncertainty
                condition.setExtension(new FSArray(cas, 1));
                condition.getExtension().addToIndexes();
                condition.setExtension(0, new Extension(cas));
                condition.getExtension(0).addToIndexes();
                condition.getExtension(0).setUrl("uncertainty");
                if (mention.getUncertainty() == 1) {
                    condition.getExtension(0).setValueString(Util.instantiatePrimitiveWithValue(FHIRString.class, cas, "uncertain", mention.getBegin(), mention.getEnd()));
                } else {
                    condition.getExtension(0).setValueString(Util.instantiatePrimitiveWithValue(FHIRString.class, cas, "certain", mention.getBegin(), mention.getEnd()));
                }
                condition.addToIndexes();
                fmh.setCondition(new FSArray(cas, 1));
                fmh.setCondition(0, condition);
            }
            fmh.addToIndexes();
        }
    }


    /**
     * Span class to prevent duplicates
     */
    private class Span {
        int begin, end;
        String type;

        public Span(int begin, int end, String type) {
            this.begin = begin;
            this.end = end;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Span span = (Span) o;
            return begin == span.begin &&
                    end == span.end &&
                    Objects.equals(type, span.type);
        }

        @Override
        public int hashCode() {

            return Objects.hash(begin, end, type);
        }
    }
}
