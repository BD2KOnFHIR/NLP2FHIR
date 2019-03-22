package edu.mayo.bsi.nlp2fhir.transformers;

import edu.mayo.bsi.nlp.vts.SNOMEDCT;
import edu.mayo.bsi.nlp.vts.UMLS;
import edu.mayo.bsi.nlp2fhir.Util;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.AnatomicalSiteMention;
import org.apache.ctakes.typesystem.type.textsem.DiseaseDisorderMention;
import org.apache.ctakes.typesystem.type.textsem.SignSymptomMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.Code;
import org.hl7.fhir.CodeableConcept;
import org.hl7.fhir.Coding;
import org.hl7.fhir.DosageInstruction;
import org.hl7.fhir.FHIRString;
import org.hl7.fhir.MedicationStatement;
import org.hl7.fhir.Uri;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Imports cTAKES annotations in regards to medication dosages into FHIR.<br>
 * Depends on: {@link MedExtractorsToFHIRMedications}
 */
public class CTAKESToFHIRMedications extends JCasAnnotator_ImplBase {

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
    }

    @Override// TODO: code here could be a LOT cleaner, optimization
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        // Sites
        Map<AnatomicalSiteMention, Collection<Sentence>> siteToSentenceIndex = JCasUtil.indexCovering(jCas, AnatomicalSiteMention.class, Sentence.class);
        Map<Sentence, Collection<MedicationStatement>> sentenceToMSIndex = JCasUtil.indexCovered(jCas, Sentence.class, MedicationStatement.class);
        for (AnatomicalSiteMention mention : JCasUtil.select(jCas, AnatomicalSiteMention.class)) {
            // Check to see if this site can be associated with any MedicationStatements
            MedicationStatement statement = null;
            for (Sentence s : siteToSentenceIndex.getOrDefault(mention, new ArrayList<>())) {
                statement = sentenceToMSIndex.getOrDefault(s, new ArrayList<>()).stream().findFirst().orElse(null);
                if (statement != null) {
                    break;
                }
            }
            if (statement == null) { // No candidate Medication Statement
                continue;
            }
            // Find results of dictionary lookup
            FSArray arr = mention.getOntologyConceptArr();
            if (arr != null) {
                for (FeatureStructure fs : arr.toArray()) {
                    if (fs instanceof UmlsConcept) {
                        UmlsConcept c = (UmlsConcept) fs;
                        String code = c.getCode(); // Vocab specific
                        String term = c.getPreferredText();
                        String rootItem = "91723000"; // As defined in http://hl7.org/fhir/valueset-approach-site-codes.html
                        if (code == null || term == null || !SNOMEDCT.isChild(code, rootItem)) {
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
                        }
                        DosageInstruction di;
                        FSArray dosageArr = statement.getDosage();
                        if (dosageArr != null) {
                            di = (DosageInstruction) dosageArr.get(0);
                        } else {
                            statement.setDosage(new FSArray(jCas, 1));
                            di = new DosageInstruction(jCas, mention.getBegin(), mention.getEnd());
                            di.addToIndexes();
                            statement.setDosage(0, di);
                        }
                        CodeableConcept siteConcept = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                        siteConcept.addToIndexes();
                        siteConcept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                        coding.addToIndexes();
                        coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                        Code fhirCode = new Code(jCas, mention.getBegin(), mention.getEnd());
                        fhirCode.addToIndexes();
                        fhirCode.setValue(code);
                        coding.setCode(fhirCode);
                        coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        siteConcept.setCoding(new FSArray(jCas, 1));
                        siteConcept.setCoding(0, coding);
                        di.setSite(siteConcept);
                        break;
                    }
                }
            }
        }

        // Reason - Sign Symptom Mentions
        Map<SignSymptomMention, Collection<Sentence>> signToSentenceIndex = JCasUtil.indexCovering(jCas, SignSymptomMention.class, Sentence.class);
        for (SignSymptomMention mention : JCasUtil.select(jCas, SignSymptomMention.class)) {
            // Check to see if this site can be associated with any MedicationStatements
            MedicationStatement statement = null;
            for (Sentence s : signToSentenceIndex.getOrDefault(mention, new ArrayList<>())) {
                statement = sentenceToMSIndex.getOrDefault(s, new ArrayList<>()).stream().findFirst().orElse(null);
                if (statement != null) {
                    break;
                }
            }
            if (statement == null) { // No candidate Medication Statement
                continue;
            }
            FSArray arr = mention.getOntologyConceptArr();
            if (arr != null) {
                for (FeatureStructure fs : arr.toArray()) {
                    if (fs instanceof UmlsConcept) {
                        UmlsConcept c = (UmlsConcept) fs;
                        String code = c.getCode(); // Vocab specific
                        String term = c.getPreferredText();
                        String rootItem = "404684003"; // As defined in https://www.hl7.org/fhir/valueset-condition-code.html
                        if (code == null || term == null || !SNOMEDCT.isChild(code, rootItem)) {
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
                        }
                        FSArray reasonArr = statement.getReasonForUseCodeableConcept();
                        if (reasonArr != null) {
                            FSArray newArr = new FSArray(jCas, reasonArr.size() + 1); // Have to increase array size first
                            for (int i = 1; i < newArr.size(); i++) {
                                newArr.set(i, reasonArr.get(i - 1)); // Copy all into array tail
                            }
                            statement.setReasonForUseCodeableConcept(newArr);
                        } else {
                            statement.setReasonForUseCodeableConcept(new FSArray(jCas, 1));
                        }
                        CodeableConcept reasonConcept = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                        reasonConcept.addToIndexes();
                        reasonConcept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                        coding.addToIndexes();
                        coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                        Code fhirCode = new Code(jCas, mention.getBegin(), mention.getEnd());
                        fhirCode.addToIndexes();
                        fhirCode.setValue(code);
                        coding.setCode(fhirCode);
                        coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        reasonConcept.setCoding(new FSArray(jCas, 1));
                        reasonConcept.setCoding(0, coding);
                        statement.setReasonForUseCodeableConcept(0, reasonConcept);
                        Util.expand(statement, mention.getBegin(), mention.getEnd());
                        // Also add to as needed if present
                        DosageInstruction di = null;
                        FSArray dosageArr = statement.getDosage();
                        if (dosageArr != null) {
                            di = (DosageInstruction) dosageArr.get(0);
                        }
                        if (di != null) {
                            if (di.getAsNeededBoolean() != null && di.getAsNeededBoolean().getValue().equalsIgnoreCase("true")) {
                                di.setAsNeededCodeableConcept(reasonConcept);
                                Util.expand(di, mention.getBegin(), mention.getEnd());
                                Util.expand(statement, mention.getBegin(), mention.getEnd());
                            }
                        }
                        break;
                    }
                }
            }
        }

        // Dosage reason - Disease Disorder Mentions
        Map<DiseaseDisorderMention, Collection<Sentence>> diseaseToSentenceIndex = JCasUtil.indexCovering(jCas, DiseaseDisorderMention.class, Sentence.class);
        for (DiseaseDisorderMention mention : JCasUtil.select(jCas, DiseaseDisorderMention.class)) {
            // Check to see if this site can be associated with any MedicationStatements
            MedicationStatement statement = null;
            for (Sentence s : diseaseToSentenceIndex.getOrDefault(mention, new ArrayList<>())) {
                statement = sentenceToMSIndex.getOrDefault(s, new ArrayList<>()).stream().findFirst().orElse(null);
                if (statement != null) {
                    break;
                }
            }
            if (statement == null) { // No candidate Medication Statement
                continue;
            }
            FSArray arr = mention.getOntologyConceptArr();
            if (arr != null) {
                for (FeatureStructure fs : arr.toArray()) {
                    if (fs instanceof UmlsConcept) {
                        UmlsConcept c = (UmlsConcept) fs;
                        String code = c.getCode(); // Vocab specific
                        String term = c.getPreferredText();
                        String rootItem = "404684003"; // As defined in https://www.hl7.org/fhir/valueset-condition-code.html
                        if (code == null || term == null || !SNOMEDCT.isChild(code, rootItem)) {
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
                        }
                        FSArray reasonArr = statement.getReasonForUseCodeableConcept();
                        if (reasonArr != null) {
                            FSArray newArr = new FSArray(jCas, reasonArr.size() + 1); // Have to increase array size first
                            for (int i = 1; i < newArr.size(); i++) {
                                newArr.set(i, reasonArr.get(i - 1)); // Copy all into array tail
                            }
                            statement.setReasonForUseCodeableConcept(newArr);
                        } else {
                            statement.setReasonForUseCodeableConcept(new FSArray(jCas, 1));
                        }
                        CodeableConcept reasonConcept = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                        reasonConcept.addToIndexes();
                        reasonConcept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                        coding.addToIndexes();
                        coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                        Code fhirCode = new Code(jCas, mention.getBegin(), mention.getEnd());
                        fhirCode.addToIndexes();
                        fhirCode.setValue(code);
                        coding.setCode(fhirCode);
                        coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        reasonConcept.setCoding(new FSArray(jCas, 1));
                        reasonConcept.setCoding(0, coding);
                        statement.setReasonForUseCodeableConcept(0, reasonConcept);
                        Util.expand(statement, mention.getBegin(), mention.getEnd());
                        DosageInstruction di = null;
                        FSArray dosageArr = statement.getDosage();
                        if (dosageArr != null) {
                            di = (DosageInstruction) dosageArr.get(0);
                        }
                        if (di != null) {
                            if (di.getAsNeededBoolean() != null && di.getAsNeededBoolean().getValue().equalsIgnoreCase("true")) {
                                di.setAsNeededCodeableConcept(reasonConcept);
                                Util.expand(di, mention.getBegin(), mention.getEnd());
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

}
