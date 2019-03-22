package edu.mayo.bsi.nlp2fhir.transformers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.mayo.bsi.nlp.vts.SNOMEDCT;
import edu.mayo.bsi.nlp.vts.UMLS;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.nlp.Section;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.relation.DegreeOfTextRelation;
import org.apache.ctakes.typesystem.type.relation.LocationOfTextRelation;
import org.apache.ctakes.typesystem.type.relation.ManifestationOfTextRelation;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Converts cTAKES Annotations to FHIR format for the problem list section<br>
 * Depends extensively on the cTAKES pipeline
 * TODO: define exactly what elements
 */
public class CTAKESToFHIRProblemList extends JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
//        Section s = new Section(jCas, 0, jCas.getDocumentText().length());
        Set<Span> constructed = new HashSet<>();
        for (Section s : JCasUtil.select(jCas, Section.class)) {
//            if (s.getId().equalsIgnoreCase("11450-4")
//                    || s.getId().equalsIgnoreCase("29762-2")
//                    || s.getId().equalsIgnoreCase("10164-2")
//                    || s.getId().equalsIgnoreCase("59768-2")
//                    || s.getId().equalsIgnoreCase("11348-0")
//                    || s.getId().equalsIgnoreCase("30954-2")
//                    || s.getId().equalsIgnoreCase("29299-5")
//                    || s.getId().equalsIgnoreCase("47519-4")) {
                buildConditions(jCas, s, constructed);
                buildProcedures(jCas, s, constructed);
                buildDevices(jCas, s, constructed);
//            }
        }
    }

    private void buildConditions(JCas jCas, Section s, Set<Span> constructed) {
        // Build relation maps TODO we really don't need to be duplicating this
        Multimap<EventMention, AnatomicalSiteMention> eventToSiteMap = HashMultimap.create();
        Multimap<SignSymptomMention, DiseaseDisorderMention> signToManifestationMap = HashMultimap.create();
        Multimap<DiseaseDisorderMention, DiseaseDisorderMention> diseaseToManifestationMap = HashMultimap.create();
        Map<EventMention, Modifier> degreeOfMap = new HashMap<>();

        for (LocationOfTextRelation locationRel : JCasUtil.select(jCas, LocationOfTextRelation.class)) {
            if (locationRel.getArg1() == null || locationRel.getArg2() == null) {
                continue;
            }
            Annotation arg1 = locationRel.getArg1().getArgument();
            Annotation arg2 = locationRel.getArg2().getArgument();
            AnatomicalSiteMention site;
            EventMention target;
            // specified by cTAKES: LocationOfRelationExtractorAnnotator
            if (arg1 instanceof EventMention && arg2 instanceof AnatomicalSiteMention) { // Order specified by cTAKES relation extractor
                site = (AnatomicalSiteMention) arg2;
                target = (EventMention) arg1;
            } else {
                continue;
            }
            eventToSiteMap.put(target, site);
        }
        for (ManifestationOfTextRelation manifestationRel : JCasUtil.select(jCas, ManifestationOfTextRelation.class)) {
            if (manifestationRel.getArg1() == null || manifestationRel.getArg2() == null) {
                continue;
            }
            Annotation arg1 = manifestationRel.getArg1().getArgument();
            Annotation arg2 = manifestationRel.getArg2().getArgument();
            // specified by cTAKES: ManifestationOfRelationExtractorAnnotator
            if (arg2 instanceof DiseaseDisorderMention) {
                if (arg1 instanceof SignSymptomMention) {
                    signToManifestationMap.put((SignSymptomMention) arg1, (DiseaseDisorderMention) arg2);
                } else if (arg1 instanceof DiseaseDisorderMention) {
                    diseaseToManifestationMap.put((DiseaseDisorderMention) arg1, (DiseaseDisorderMention) arg2);
                }
            }
        }
        for (DegreeOfTextRelation degreeRel : JCasUtil.select(jCas, DegreeOfTextRelation.class)) {
            if (degreeRel.getArg1() == null || degreeRel.getArg2() == null) {
                continue;
            }
            Annotation arg1 = degreeRel.getArg1().getArgument();
            Annotation arg2 = degreeRel.getArg2().getArgument();
            // specified by cTAKES: DegreeOfRelationExtractorAnnotator
            if (arg1 instanceof EventMention && arg2 instanceof Modifier) {
                degreeOfMap.put((EventMention) arg1, (Modifier) arg2);
            }
        }
        for (DiseaseDisorderMention mention : JCasUtil.selectCovered(jCas, DiseaseDisorderMention.class, s)) {
            Span span = new Span(mention.getBegin(), mention.getEnd(), "Condition");
            if (constructed.contains(span)) {
                continue;
            } else {
                constructed.add(span);
            }
            Condition condition = new Condition(jCas);
            Util.expand(condition, mention.getBegin(), mention.getEnd());
            if (mention.getOntologyConceptArr() != null) {
                CodeableConcept concept = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                concept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, mention.getCoveredText(), mention.getBegin(), mention.getEnd()));
                FSArray conceptArr = mention.getOntologyConceptArr();
                for (int j = 0; j < conceptArr.size(); j++) {
                    FeatureStructure fs = conceptArr.get(j);
                    if (!(fs instanceof UmlsConcept)) {
                        throw new AssertionError("Ontology Concept not a UMLS Concept!");
                    }
                    UmlsConcept c = (UmlsConcept) fs;
                    String code = null; // Vocab specific
                    String term = c.getPreferredText();
                    String rootItem = "404684003"; // As defined in http://hl7.org/fhir/ValueSet/condition-code
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
                        concept.setCoding(new FSArray(jCas, 1));
                        Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                        coding.addToIndexes();
                        coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                        coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, code, mention.getBegin(), mention.getEnd()));
                        coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        concept.setCoding(0, coding);
                        break;
                    }

                }
                concept.addToIndexes();
                condition.setCode(concept);
                Util.expand(condition, concept.getBegin(), concept.getEnd());
            }
            // Condition Body Site
            if (eventToSiteMap.containsKey(mention)) {
                Collection<AnatomicalSiteMention> sites = eventToSiteMap.get(mention);
//            Collection<AnatomicalSiteMention> sites = new HashSet<>();
//            for (Chunk c : JCasUtil.selectCovering(jCas, Chunk.class, condition)) {
//                sites.addAll(JCasUtil.selectCovered(jCas, AnatomicalSiteMention.class, c));
//            }
//            if (sites.size() > 0) {
                FSArray arr = new FSArray(jCas, sites.size());
                int i = 0;
                for (AnatomicalSiteMention site : sites) {
                    CodeableConcept concept = new CodeableConcept(jCas, site.getBegin(), site.getEnd());
                    concept.setText(
                            Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, site.getCoveredText(), site.getBegin(), site.getEnd()));
                    FSArray conceptArr = site.getOntologyConceptArr();
                    for (int j = 0; j < conceptArr.size(); j++) {
                        FeatureStructure fs = conceptArr.get(j);
                        if (!(fs instanceof UmlsConcept)) {
                            throw new AssertionError("Ontology Concept not a UMLS Concept!");
                        }
                        UmlsConcept c = (UmlsConcept) fs;
                        String code = null; // Vocab specific
                        String term = c.getPreferredText();
                        String rootItem = "442083009"; // As defined in http://hl7.org/fhir/ValueSet/body-site
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
                            concept.setCoding(new FSArray(jCas, 1));
                            Coding coding = new Coding(jCas, site.getBegin(), site.getEnd());
                            coding.addToIndexes();
                            coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", site.getBegin(), site.getEnd()));
                            coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, code, site.getBegin(), site.getEnd()));
                            coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, site.getBegin(), site.getEnd()));
                            concept.setCoding(0, coding);
                            break;
                        }

                    }
                    arr.set(i++, concept);
//                    Util.expand(condition, site.getBegin(), site.getEnd());
                }
                arr.addToIndexes();
                condition.setBodySite(arr);
            }
            // Condition Manifestation
            if (diseaseToManifestationMap.containsKey(mention)) {
                Collection<DiseaseDisorderMention> manifestations = diseaseToManifestationMap.get(mention);
                FSArray arr = new FSArray(jCas, manifestations.size());
                int i = 0;
                for (DiseaseDisorderMention manifestation : manifestations) {
                    CodeableConcept concept = new CodeableConcept(jCas, manifestation.getBegin(), manifestation.getEnd());
                    concept.setText(
                            Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, manifestation.getCoveredText(), manifestation.getBegin(), manifestation.getEnd()));
                    FSArray conceptArr = manifestation.getOntologyConceptArr();
                    for (int j = 0; j < conceptArr.size(); j++) {
                        FeatureStructure fs = conceptArr.get(j);
                        if (!(fs instanceof UmlsConcept)) {
                            throw new AssertionError("Ontology Concept not a UMLS Concept!");
                        }
                        UmlsConcept c = (UmlsConcept) fs;
                        String code = null; // Vocab specific
                        String term = c.getPreferredText();
                        String rootItem = "404684003"; // As defined in http://hl7.org/fhir/ValueSet/manifestation-or-symptom
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
                            concept.setCoding(new FSArray(jCas, 1));
                            Coding coding = new Coding(jCas, manifestation.getBegin(), manifestation.getEnd());
                            coding.addToIndexes();
                            coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", manifestation.getBegin(), manifestation.getEnd()));
                            coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, code, manifestation.getBegin(), manifestation.getEnd()));
                            coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, manifestation.getBegin(), manifestation.getEnd()));
                            concept.setCoding(0, coding);
                            break;
                        }

                    }
                    arr.set(i++, concept);
//                    Util.expand(condition, manifestation.getBegin(), manifestation.getEnd());
                }
                arr.addToIndexes();
                condition.setEvidence(arr);
            }
            // Condition degree of
            if (degreeOfMap.containsKey(mention)) {
                Modifier severity = degreeOfMap.get(mention);
                CodeableConcept concept = new CodeableConcept(jCas, severity.getBegin(), severity.getEnd());
                concept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, severity.getCoveredText(), severity.getBegin(), severity.getEnd()));
                FSArray conceptArr = severity.getOntologyConceptArr();
                if (conceptArr != null) {
                    for (int j = 0; j < conceptArr.size(); j++) {
                        FeatureStructure fs = conceptArr.get(j);
                        if (!(fs instanceof UmlsConcept)) {
                            throw new AssertionError("Ontology Concept not a UMLS Concept!");
                        }
                        UmlsConcept c = (UmlsConcept) fs;
                        String code = null; // Vocab specific
                        String term = c.getPreferredText();
                        Set<String> valueSet = new HashSet<>(); // As defined in http://hl7.org/fhir/ValueSet/condition-severity
                        valueSet.add("24484000");
                        valueSet.add("6736007");
                        valueSet.add("255604002");
                        try {
                            boolean hasInterestedParent = false;
                            for (String snomedCode : UMLS.getSourceCodesForVocab(UMLS.UMLSSourceVocabulary.SNOMEDCT_US, c.getCui())) {
                                code = snomedCode;
                                hasInterestedParent = valueSet.contains(snomedCode);
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
                            concept.setCoding(new FSArray(jCas, 1));
                            Coding coding = new Coding(jCas, severity.getBegin(), severity.getEnd());
                            coding.addToIndexes();
                            coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", severity.getBegin(), severity.getEnd()));
                            coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, code, severity.getBegin(), severity.getEnd()));
                            coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, severity.getBegin(), severity.getEnd()));
                            concept.setCoding(0, coding);
                            break;
                        }

                    }
                }
                concept.addToIndexes();
                condition.setSeverity(concept);
//                Util.expand(condition, severity.getBegin(), severity.getEnd());
            }
            // Negation/abatement
            if (mention.getPolarity() == -1) {
                condition.setAbatementString(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, "negative", mention.getBegin(), mention.getEnd()));
            } else {
                condition.setAbatementString(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, "positive", mention.getBegin(), mention.getEnd()));
            }
            // Uncertainty
            condition.setExtension(new FSArray(jCas, 1));
            condition.getExtension().addToIndexes();
            condition.setExtension(0, new Extension(jCas));
            condition.getExtension(0).addToIndexes();
            condition.getExtension(0).setUrl("uncertainty");
            if (mention.getUncertainty() == 1) {
                condition.getExtension(0).setValueString(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, "uncertain", mention.getBegin(), mention.getEnd()));
            } else {
                condition.getExtension(0).setValueString(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, "certain", mention.getBegin(), mention.getEnd()));
            }
            condition.addToIndexes();
        }
    }

    private void buildProcedures(JCas jCas, Section s, Set<Span> constructed) {
        // Build relation maps TODO we really don't need to be duplicating this
        Multimap<EventMention, AnatomicalSiteMention> eventToSiteMap = HashMultimap.create();
        for (LocationOfTextRelation locationRel : JCasUtil.select(jCas, LocationOfTextRelation.class)) {
            if (locationRel.getArg1() == null || locationRel.getArg2() == null) {
                continue;
            }
            Annotation arg1 = locationRel.getArg1().getArgument();
            Annotation arg2 = locationRel.getArg2().getArgument();
            AnatomicalSiteMention site;
            EventMention target;
            // specified by cTAKES: LocationOfRelationExtractorAnnotator
            if (arg1 instanceof EventMention && arg2 instanceof AnatomicalSiteMention) { // Order specified by cTAKES relation extractor
                site = (AnatomicalSiteMention) arg2;
                target = (EventMention) arg1;
            } else {
                continue;
            }
            eventToSiteMap.put(target, site);
        }
        for (ProcedureMention mention : JCasUtil.selectCovered(jCas, ProcedureMention.class, s)) {
            Span span = new Span(mention.getBegin(), mention.getEnd(), "Procedure");
            if (constructed.contains(span)) {
                continue;
            } else {
                constructed.add(span);
            }
            Procedure procedure = new Procedure(jCas);
            Util.expand(procedure, mention.getBegin(), mention.getEnd());
            if (mention.getOntologyConceptArr() != null) {
                CodeableConcept concept = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                concept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, mention.getCoveredText(), mention.getBegin(), mention.getEnd()));
                FSArray conceptArr = mention.getOntologyConceptArr();
                for (int j = 0; j < conceptArr.size(); j++) {
                    FeatureStructure fs = conceptArr.get(j);
                    if (!(fs instanceof UmlsConcept)) {
                        throw new AssertionError("Ontology Concept not a UMLS Concept!");
                    }
                    UmlsConcept c = (UmlsConcept) fs;
                    String code = null; // Vocab specific
                    String term = c.getPreferredText();
                    String rootItem = "71388002"; // As defined in http://hl7.org/fhir/ValueSet/procedure-code
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
                        concept.setCoding(new FSArray(jCas, 1));
                        Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                        coding.addToIndexes();
                        coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                        coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, code, mention.getBegin(), mention.getEnd()));
                        coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        concept.setCoding(0, coding);
                        break;
                    }

                }
                concept.addToIndexes();
                procedure.setCode(concept);
                Util.expand(procedure, concept.getBegin(), concept.getEnd());
            }
            // Procedure Body Site
            if (eventToSiteMap.containsKey(mention)) {
                Collection<AnatomicalSiteMention> sites = eventToSiteMap.get(mention);
//            Collection<AnatomicalSiteMention> sites = new HashSet<>();
//            for (Chunk c : JCasUtil.selectCovering(jCas, Chunk.class, procedure)) {
//                sites.addAll(JCasUtil.selectCovered(jCas, AnatomicalSiteMention.class, c));
//            }
//            if (sites.size() > 0) {
                FSArray arr = new FSArray(jCas, sites.size());
                int i = 0;
                for (AnatomicalSiteMention site : sites) {
                    CodeableConcept concept = new CodeableConcept(jCas, site.getBegin(), site.getEnd());
                    concept.setText(
                            Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, site.getCoveredText(), site.getBegin(), site.getEnd()));
                    FSArray conceptArr = site.getOntologyConceptArr();
                    for (int j = 0; j < conceptArr.size(); j++) {
                        FeatureStructure fs = conceptArr.get(j);
                        if (!(fs instanceof UmlsConcept)) {
                            throw new AssertionError("Ontology Concept not a UMLS Concept!");
                        }
                        UmlsConcept c = (UmlsConcept) fs;
                        String code = null; // Vocab specific
                        String term = c.getPreferredText();
                        String rootItem = "442083009"; // As defined in http://hl7.org/fhir/ValueSet/body-site
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
                            concept.setCoding(new FSArray(jCas, 1));
                            Coding coding = new Coding(jCas, site.getBegin(), site.getEnd());
                            coding.addToIndexes();
                            coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", site.getBegin(), site.getEnd()));
                            coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, code, site.getBegin(), site.getEnd()));
                            coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, site.getBegin(), site.getEnd()));
                            concept.setCoding(0, coding);
                            break;
                        }

                    }
                    arr.set(i++, concept);
//                    Util.expand(procedure, site.getBegin(), site.getEnd());
                }
                arr.addToIndexes();
                procedure.setBodySite(arr);
            }
            procedure.addToIndexes();
        }
    }

    private void buildDevices(JCas jCas, Section s, Set<Span> constructed) {
        // TODO
    }

    private void buildFamilyMemberHistory(JCas jCas, Section s) {
        // TODO
    }

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
