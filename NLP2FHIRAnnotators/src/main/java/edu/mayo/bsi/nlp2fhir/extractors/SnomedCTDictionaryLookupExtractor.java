package edu.mayo.bsi.nlp2fhir.extractors;

import edu.mayo.bsi.nlp.vts.SNOMEDCT;
import edu.mayo.bsi.nlp.vts.UMLS;
import edu.mayo.bsi.nlp2fhir.Util;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.AnatomicalSiteMention;
import org.apache.ctakes.typesystem.type.textsem.EntityMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Performs dictionary lookup at a sentence level using results from a SnomedCT extraction
 */
public class SnomedCTDictionaryLookupExtractor extends JCasAnnotator_ImplBase {


    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        // Lookup indexes for performance
        Map<EntityMention, Collection<Sentence>> mentionToSiteIndex = JCasUtil.indexCovering(jCas, EntityMention.class, Sentence.class);
        Map<Sentence, Collection<MedicationStatement>> sentenceToMSIndex = JCasUtil.indexCovered(jCas, Sentence.class, MedicationStatement.class);
        // Go through extracted entity mentions and instantiate relevant annotations if in one of the hierarchies of
        // interest

        for (EntityMention mention : JCasUtil.select(jCas, EntityMention.class)) {
            if (mention instanceof AnatomicalSiteMention) {
                continue; // Part of cTAKES default extract, do not process this
            }
            // Check to see if this mention can be associated with any MedicationStatements
            MedicationStatement statement = null;
            for (Sentence s : mentionToSiteIndex.getOrDefault(mention, new ArrayList<>())) {
                statement = sentenceToMSIndex.getOrDefault(s, new ArrayList<>()).stream().findFirst().orElse(null);
                if (statement != null) {
                    break;
                }
            }
            if (statement == null) { // No candidate Medication Statement
                continue;
            }
            FSArray ontConcepts = mention.getOntologyConceptArr();
            // Additional Instructions
            if (ontConcepts != null) {
                for (FeatureStructure fs : ontConcepts.toArray()) {
                    if (!(fs instanceof UmlsConcept)) {
                        continue;
                    }
                    UmlsConcept c = (UmlsConcept) fs;
                    String code = c.getCode(); // Vocab specific
                    String term = c.getPreferredText();
                    String rootItem = "419492006"; // As defined in http://hl7.org/fhir/ValueSet/additional-instruction-codes
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
                    // We have found a SNOMEDCT code/term within our defined hierarchy
                    CodeableConcept additionalInstructions = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                    additionalInstructions.addToIndexes();
                    additionalInstructions.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                    Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                    coding.addToIndexes();
                    coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                    Code fhirCode = new Code(jCas, mention.getBegin(), mention.getEnd());
                    fhirCode.addToIndexes();
                    fhirCode.setValue(code);
                    coding.setCode(fhirCode);
                    coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                    additionalInstructions.setCoding(new FSArray(jCas, 1));
                    additionalInstructions.setCoding(0, coding);
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
                    FSArray instructions = di.getAdditionalInstructions();
                    if (instructions != null) {
                        FSArray newArr = new FSArray(jCas, instructions.size() + 1); // Have to increase array size first
                        for (int i = 1; i < newArr.size(); i++) {
                            newArr.set(i, instructions.get(i - 1)); // Copy all into array tail
                        }
                        di.setAdditionalInstructions(newArr);
                    } else {
                        di.setAdditionalInstructions(new FSArray(jCas, 1));
                    }
                    di.setAdditionalInstructions(0, additionalInstructions);
                    break;
                }
            }
            // Administration Method
            if (ontConcepts != null) {
                for (FeatureStructure fs : ontConcepts.toArray()) {
                    if (!(fs instanceof UmlsConcept)) {
                        continue;
                    }
                    UmlsConcept c = (UmlsConcept) fs;
                    String code = c.getCode(); // Vocab specific
                    String term = c.getPreferredText();
                    String rootItem = "422096002"; // As defined in http://hl7.org/fhir/ValueSet/administration-method-codes
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
                    // We have found a SNOMEDCT code/term within our defined hierarchy
                    CodeableConcept method = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                    method.addToIndexes();
                    method.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                    Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                    coding.addToIndexes();
                    coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://snomed.info/sct", mention.getBegin(), mention.getEnd()));
                    Code fhirCode = new Code(jCas, mention.getBegin(), mention.getEnd());
                    fhirCode.addToIndexes();
                    fhirCode.setValue(code);
                    coding.setCode(fhirCode);
                    coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                    method.setCoding(new FSArray(jCas, 1));
                    method.setCoding(0, coding);
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
                    di.setMethod(method);
                    break;
                }
            }
        }
    }
}
