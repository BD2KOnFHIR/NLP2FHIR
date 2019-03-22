package edu.mayo.bsi.nlp2fhir.transformers;

import edu.mayo.bsi.nlp2fhir.RegexpStatements;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.valuesets.TimingAbbreviation;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.*;
import org.hl7.fhir.Boolean;
import org.hl7.fhir.Integer;
import org.ohnlp.medtagger.type.ConceptMention;
import org.ohnlp.medtime.type.MedTimex3;
import org.ohnlp.medxn.type.Drug;
import org.ohnlp.medxn.type.MedAttr;
import org.ohnlp.typesystem.type.textspan.Sentence;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports annotations from the MedExtractor family of NLP tools (MedXN, MedTime, etc) v1.0.1 into the FHIR typesystem
 * TODO MedicationStatement->Medication Reference
 * TODO special cases code cleanup, legibility
 * TODO iterate through multiple medattrs when present
 */
public class MedExtractorsToFHIRMedications extends JCasAnnotator_ImplBase {


    /**
     * Processes and makes appropriate modifications for an input jCAS
     * TODO: Remove/refactor debug
     *
     * @param jCas A Java Cover Class Common Analysis System object representing a document to import
     * @throws AnalysisEngineProcessException Upon any runtime errors (will be forwarded as the cause)
     */
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        try {
            // Cache lookups for performance reasons (used for dosages)
            Map<Sentence, Collection<MedTimex3>> sentenceToMedTimeLookup = JCasUtil.indexCovered(jCas, Sentence.class, MedTimex3.class);
            Map<Drug, Collection<Sentence>> drugToSentenceLookup = JCasUtil.indexCovering(jCas, Drug.class, Sentence.class);
            Map<MedAttr, Collection<Sentence>> attributeToSentenceLookup = JCasUtil.indexCovering(jCas, MedAttr.class, Sentence.class);
            // Construct a MedicationStatement for each Drug
            Collection<Drug> medXNDrugs =
                    JCasUtil.select(jCas, Drug.class);
            for (Drug drug : medXNDrugs) {
                MedicationStatement med = new MedicationStatement(jCas, drug.getBegin(), drug.getEnd());
                Medication m = importMedicationFHIRElement(jCas, drug);
                med.setMedicationCodeableConcept(m.getCode());
                DosageInstruction dosage = importDosageFHIRElement(jCas, drug, sentenceToMedTimeLookup, drugToSentenceLookup, attributeToSentenceLookup);
                FSArray dosageInstructions = new FSArray(jCas, 1);
                dosageInstructions.addToIndexes();
                med.setDosage(dosageInstructions);
                med.setDosage(0, dosage);
                if (med.getBegin() > dosage.getBegin() && (dosage.getBegin() != 0 && dosage.getEnd() != 0)) {
                    med.setBegin(dosage.getBegin());
                }
                if (med.getEnd() < dosage.getEnd() && (dosage.getBegin() != 0 && dosage.getEnd() != 0)) {
                    med.setEnd(dosage.getEnd());
                }
                med.addToIndexes();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a FHIR DosageInstruction from an input MedXN Drug
     *
     * @param jCas                      jCAS from which to pull input data as well as output converted FHIR types
     * @param drug                      The MedXN representation of the drug mention to import to FHIR format
     * @param sentenceToMedTimeLookup   An index mapping sentences to MedTime annotations
     * @param drugToSentenceLookup      An index mapping drugs to sentence annotations
     * @param attributeToSentenceLookup An index mapping MedAttr from MedXN to their covering sentences
     * @return A FHIR representation of the input drug's dosage
     */
    private DosageInstruction importDosageFHIRElement(JCas jCas, Drug drug, Map<Sentence, Collection<MedTimex3>> sentenceToMedTimeLookup, Map<Drug, Collection<Sentence>> drugToSentenceLookup, Map<MedAttr, Collection<Sentence>> attributeToSentenceLookup) {

        DosageInstruction ret = new DosageInstruction(jCas);
        // Retrieve
        // Populate Timing Information
        Timing timing = new Timing(jCas);
        TimingRepeat repeatObject = new TimingRepeat(jCas);
        // - Set frequency information
        MedAttr freqAttr = getMedAttr("frequency", drug.getAttrs());
        if (freqAttr != null) {
            Matcher m = RegexpStatements.FREQPERIOD.matcher(freqAttr.getCoveredText());
            if (m.find()) {
                ret.setBegin(freqAttr.getBegin());
                ret.setEnd(freqAttr.getEnd());
                // see regex string documentation
                String freq1 = m.group(1);
                String freq2 = m.group(2);
                String freq3 = m.group(3); // -ly special case
                String freq4 = m.group(4); //  every (other) special case
                String everyOther = m.group(5); // - contains other
                String period1 = m.group(7);
                String rangeIndicator = m.group(8);
                String period2 = m.group(9);
                String periodUnit = m.group(10);
                String periodLy = m.group(11);
                // positional variables
                int freqStart = freq1 == null ? freqAttr.getBegin() : freqAttr.getBegin() + m.start(1);
                int freqEnd = freq1 == null ? freqAttr.getEnd() : freqAttr.getBegin() + m.end(1);
                int freqRangeStart = freq2 == null ? freqAttr.getBegin() : freqAttr.getBegin() + m.start(2);
                int freqRangeEnd = freq2 == null ? freqAttr.getEnd() : freqAttr.getBegin() + m.end(2);
                int perStart = period1 == null ? freqAttr.getBegin() : freqAttr.getBegin() + m.start(7);
                int perEnd = period1 == null ? freqAttr.getEnd() : freqAttr.getBegin() + m.end(7);
                int perMaxStart = period2 == null ? freqAttr.getBegin() : freqAttr.getBegin() + m.start(9);
                int perMaxEnd = period2 == null ? freqAttr.getEnd() : freqAttr.getBegin() + m.end(9);
                int perUnitStart = periodUnit == null ? freqAttr.getBegin() : freqAttr.getBegin() + m.start(10);
                int perUnitEnd = periodUnit == null ? freqAttr.getEnd() : freqAttr.getBegin() + m.end(10);
                // Some cleanup
                if (freq1 == null && freq4 != null) { // -ly special case
                    freq1 = "1";
                    freqStart = freqAttr.getBegin() + m.start(4);
                    freqEnd = freqAttr.getBegin() + m.end(4);
                }
                if (freq1 == null && freq3 != null) { // -ly special case
                    freq1 = freq3;
                    freqStart = freqAttr.getBegin() + m.start(3);
                    freqEnd = freqAttr.getBegin() + m.end(3);
                }
                if (rangeIndicator == null) {
                    period1 = (period1 == null ? "" : period1) + (period2 == null ? "" : period2);
                    perEnd = freqAttr.getBegin() + m.end(9);
                }
                if (periodLy != null && period1.length() == 0) {
                    period1 = "1"; // Has a ly
                    periodUnit = periodLy;
                    perStart = freqAttr.getBegin() + m.start(11);
                    perEnd = freqAttr.getBegin() + m.end(11);
                }
                // Populate CAS with Data
                if (freq1 != null) {
                    repeatObject.setFrequency(Util.instantiatePrimitiveWithValue(Integer.class, jCas, Util.normalizeNumber(freq1), freqStart, freqEnd));
                }
                if (freq2 != null) {
                    repeatObject.setFrequencyMax(Util.instantiatePrimitiveWithValue(Integer.class, jCas, Util.normalizeNumber(freq2), freqRangeStart, freqRangeEnd));
                }
                boolean periodModify = (freq4 != null && freq4.toLowerCase().contains("other")) || everyOther != null;
                // While period unit is checked for in this block, a FHIR representation is not created until later
                if (period1 != null && period1.length() > 0 && periodUnit != null) {
                    period1 = Util.normalizeNumber(period1);
                    if (periodModify) period1 = 2 * java.lang.Integer.valueOf(period1) + "";
                    repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, period1, perStart, perEnd));
                    if (rangeIndicator != null) {
                        repeatObject.setPeriodMax(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Util.normalizeNumber(period2), perMaxStart, perMaxEnd));
                    }
                } else if (periodUnit != null) { // Empty period/period not specified but unit present, assume 1 or 2
                    repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, periodModify ? "2" : "1", periodModify ? freqAttr.getBegin() + m.start(4) : perUnitStart, perUnitEnd));
                }
                if (periodUnit != null) {
                    repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, Util.transformUnitOfTime(periodUnit), perUnitStart, perUnitEnd));
                }
                // Do special case postprocessing // TODO positions areslightly off due to removal of multiple spaces
                String covered = freqAttr.getCoveredText().toLowerCase().replaceAll("-", " ").replaceAll(" {2}", " ");
                if (covered.contains("as needed") || covered.contains("prn")) { // As needed
                    int start = covered.indexOf("as needed");
                    int end;
                    if (start != -1) {
                        start += freqAttr.getBegin();
                        end = start + 9;
                    } else {
                        start = freqAttr.getBegin() + covered.indexOf("prn");
                        end = start + 3;
                    }
                    ret.setAsNeededBoolean(Util.instantiatePrimitiveWithValue(Boolean.class, jCas, true + "", start, end));
                    if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                        ret.setBegin(Math.min(ret.getBegin(), start));
                        ret.setEnd(Math.max(ret.getEnd(), end));
                    } else {
                        ret.setBegin(start);
                        ret.setEnd(end);
                    }
                }
                // on weekday1, weekday2, weekday3
                Collection<Sentence> coveringSents = attributeToSentenceLookup.getOrDefault(freqAttr, new LinkedList<>());
                for (Sentence sentence : coveringSents) {
                    covered = sentence.getCoveredText().toLowerCase().replaceAll("-", " ").replaceAll(" {2}", " ");
                    Matcher m2 = RegexpStatements.PERIOD_WEEKDAYS.matcher(covered);
                    if (m2.find()) {
                        int start = sentence.getBegin() + m2.toMatchResult().start();
                        int end = sentence.getBegin() + m2.toMatchResult().end();
                        if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                            ret.setBegin(Math.min(ret.getBegin(), start));
                            ret.setEnd(Math.max(ret.getEnd(), end));
                        } else {
                            ret.setBegin(start);
                            ret.setEnd(end);
                        }
                        String other = m2.group(1);
                        String days = m2.group(2).trim().replaceAll(",", "").replace("and", "").replaceAll(" {2}", " ");
                        String[] daysParsed = days.split("[, -]");
                        int freq = daysParsed.length;
                        int period = (other == null) ? 1 : 2;
                        int existingFreq = repeatObject.getFrequency() != null ? java.lang.Integer.valueOf(repeatObject.getFrequency().getValue()) : 1;
                        int existingFreqUpper = repeatObject.getFrequencyMax() != null ? java.lang.Integer.valueOf(repeatObject.getFrequencyMax().getValue()) : 1;
                        freq *= existingFreq;
                        int freqMax = freq * existingFreqUpper;
                        repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, period + "", ret.getBegin(), ret.getEnd()));
                        repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, "wk", ret.getBegin(), ret.getEnd()));
                        repeatObject.setFrequency(Util.instantiatePrimitiveWithValue(Integer.class, jCas, freq + "", ret.getBegin(), ret.getEnd()));
                        repeatObject.setFrequencyMax(Util.instantiatePrimitiveWithValue(Integer.class, jCas, freqMax + "", ret.getBegin(), ret.getEnd()));
                        FSArray weekdayArray = new FSArray(jCas, daysParsed.length);
                        weekdayArray.addToIndexes();
                        repeatObject.setDayOfWeek(weekdayArray);
                        for (int i = 0; i < daysParsed.length; i++) {
                            Code dayCode = new Code(jCas,
                                    sentence.getBegin() + m2.start(2), sentence.getBegin() + m2.end(2));
                            Matcher m3 = RegexpStatements.WEEKDAY_PARSER.matcher(daysParsed[i].toLowerCase());
                            if (!m3.find()) {
                                // Guaranteed true but just in case
                                continue;
                            }
                            dayCode.setValue(m3.group(1).substring(0, 3));
                            dayCode.addToIndexes();
                            repeatObject.setDayOfWeek(i, dayCode);
                        }
                    }
                }
                // Meals
                Matcher m2 = RegexpStatements.TIMING_EVENTS.matcher(covered);
                if (m2.find()) {
                    String operator = m2.group(1);
                    String meal = m2.group(2);
                    int op;
                    switch (operator) {
                        case "before":
                            op = -1;
                            break;
                        case "after":
                            op = 1;
                            break;
                        case "at":
                        case "during":
                        case "with":
                        case "every":
                        default:
                            op = 0;
                            break;
                    }
                    String code = Util.getHL7EventTimingCode(op, meal);
                    int start = freqAttr.getBegin();
                    int end = freqAttr.getEnd();
                    if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                        ret.setBegin(Math.min(ret.getBegin(), start));
                        ret.setEnd(Math.max(ret.getEnd(), end));
                    } else {
                        ret.setBegin(start);
                        ret.setEnd(end);
                    }
                    EventTiming event = new EventTiming(jCas);
                    event.setBegin(ret.getBegin());
                    event.setEnd(ret.getEnd());
                    event.setValue(code);
                    event.addToIndexes();
                    repeatObject.setWhen(event);
                    if (repeatObject.getFrequency() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequency(null);
                    }
                    if (repeatObject.getFrequencyMax() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequencyMax(null);
                    }
                    if (repeatObject.getPeriod() != null) {
                        repeatObject.setPeriod(null);
                    }
                    if (repeatObject.getPeriodMax() != null) {
                        repeatObject.setPeriodMax(null);
                    }
                    if (repeatObject.getPeriodUnit() != null) {
                        repeatObject.setPeriodUnit(null);
                    }
                }
                // Times of day
                m2 = RegexpStatements.TIME_OF_DAY.matcher(covered);
                if (m2.find()) {
                    int start = freqAttr.getBegin();
                    int end = freqAttr.getEnd();
                    if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                        ret.setBegin(Math.min(ret.getBegin(), start));
                        ret.setEnd(Math.max(ret.getEnd(), end));
                    } else {
                        ret.setBegin(start);
                        ret.setEnd(end);
                    }
                    EventTiming event = new EventTiming(jCas);
                    event.setBegin(ret.getBegin());
                    event.setEnd(ret.getEnd());
                    event.setValue(Util.getHL7EventTimingCode(0, m2.group(1)));
                    event.addToIndexes();
                    repeatObject.setWhen(event);
                    if (repeatObject.getFrequency() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequency(null);
                    }
                    if (repeatObject.getFrequencyMax() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequencyMax(null);
                    }
                    if (repeatObject.getPeriod() != null) {
                        repeatObject.setPeriod(null);
                    }
                    if (repeatObject.getPeriodMax() != null) {
                        repeatObject.setPeriodMax(null);
                    }
                    if (repeatObject.getPeriodUnit() != null) {
                        repeatObject.setPeriodUnit(null);
                    }
                }
            } else {
                // Unmatched - see if MedTime has an associated identification
                boolean flag = false;
                for (Sentence s : drugToSentenceLookup.get(drug)) { // Should only be one
                    for (MedTimex3 time : sentenceToMedTimeLookup.get(s)) {
                        String timexValue = time.getTimexValue();
                        String timeXType = time.getTimexType();
                        if (timeXType != null && timeXType.equalsIgnoreCase("SET")) {
                            Double[] timestamp = convertFromISO8601Standard(timexValue);
                            if (timestamp == null) continue;
                            // Found timestamp
                            ret.setBegin(time.getBegin());
                            ret.setEnd(time.getEnd());
                            String[] parsed = fromConvertedISO8601(timestamp);
                            if (Double.valueOf(Util.normalizeNumber(parsed[0])) % 24 == 0 && parsed[1].equalsIgnoreCase("h")) {
                                // Common pattern in MedTime
                                repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Double.valueOf(Util.normalizeNumber(parsed[0]))/24 + "", time.getBegin(), time.getEnd()));
                                repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, Util.transformUnitOfTime("day"), time.getBegin(), time.getEnd()));
                            } else {
                                repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Util.normalizeNumber(parsed[0]), time.getBegin(), time.getEnd()));
                                repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, parsed[1], time.getBegin(), time.getEnd()));
                            }
                            flag = true;
                            break;
                        }
                    }
                }
                // Check for special cases via rule based methods TODO cleanup/abstractify
                String covered = freqAttr.getCoveredText().toLowerCase().replaceAll("-", " ").replaceAll(" {2}", " ");
                if (covered.contains("as needed") || covered.contains("prn")) { // As needed
                    int start = covered.indexOf("as needed");
                    int end;
                    if (start != -1) {
                        start += freqAttr.getBegin();
                        end = start + 9;
                    } else {
                        start = freqAttr.getBegin() + covered.indexOf("prn");
                        end = start + 3;
                    }
                    ret.setAsNeededBoolean(Util.instantiatePrimitiveWithValue(Boolean.class, jCas, true + "", start, end));
                    if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                        ret.setBegin(Math.min(ret.getBegin(), start));
                        ret.setEnd(Math.max(ret.getEnd(), end));
                    } else {
                        ret.setBegin(start);
                        ret.setEnd(end);
                    }
                    flag = true;
                }
                // on weekday1, weekday2, weekday3
                Collection<Sentence> coveringSents = attributeToSentenceLookup.getOrDefault(freqAttr, new LinkedList<>());
                for (Sentence sentence : coveringSents) {
                    covered = sentence.getCoveredText().toLowerCase().replaceAll("-", " ").replaceAll(" {2}", " ");
                    Matcher m2 = RegexpStatements.PERIOD_WEEKDAYS.matcher(covered);
                    if (m2.find()) {
                        int start = sentence.getBegin() + m2.toMatchResult().start();
                        int end = sentence.getBegin() + m2.toMatchResult().end();
                        if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                            ret.setBegin(Math.min(ret.getBegin(), start));
                            ret.setEnd(Math.max(ret.getEnd(), end));
                        } else {
                            ret.setBegin(start);
                            ret.setEnd(end);
                        }
                        String other = m2.group(1);
                        String days = m2.group(2).trim().replaceAll(",", "").replace("and", "").replaceAll(" {2}", " ");
                        String[] daysParsed = days.split("[, -]");
                        int freq = daysParsed.length;
                        int period = (other == null) ? 1 : 2;
                        int existingFreq = repeatObject.getFrequency() != null ? java.lang.Integer.valueOf(repeatObject.getFrequency().getValue()) : 1;
                        int existingFreqUpper = repeatObject.getFrequencyMax() != null ? java.lang.Integer.valueOf(repeatObject.getFrequencyMax().getValue()) : 1;
                        freq *= existingFreq;
                        int freqMax = freq * existingFreqUpper;
                        repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, period + "", ret.getBegin(), ret.getEnd()));
                        repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, "wk", ret.getBegin(), ret.getEnd()));
                        repeatObject.setFrequency(Util.instantiatePrimitiveWithValue(Integer.class, jCas, freq + "", ret.getBegin(), ret.getEnd()));
                        repeatObject.setFrequencyMax(Util.instantiatePrimitiveWithValue(Integer.class, jCas, freqMax + "", ret.getBegin(), ret.getEnd()));
                        FSArray weekdayArray = new FSArray(jCas, daysParsed.length);
                        weekdayArray.addToIndexes();
                        repeatObject.setDayOfWeek(weekdayArray);
                        for (int i = 0; i < daysParsed.length; i++) {
                            Code dayCode = new Code(jCas);
                            dayCode.setBegin(sentence.getBegin() + m2.start(2));
                            dayCode.setEnd(sentence.getBegin() + m2.end(2));
                            Matcher m3 = RegexpStatements.WEEKDAY_PARSER.matcher(daysParsed[i].toLowerCase());
                            if (!m3.find()) {
                                // Guaranteed true
                                continue;
                            }
                            dayCode.setValue(m3.group(1).substring(0, 3));
                            dayCode.addToIndexes();
                            repeatObject.setDayOfWeek(i, dayCode);
                        }
                        flag = true;
                    }
                }
                // Meals
                Matcher m2 = RegexpStatements.TIMING_EVENTS.matcher(covered);
                if (m2.find()) {
                    String operator = m2.group(1);
                    String meal = m2.group(2);
                    int op;
                    switch (operator) {
                        case "before":
                            op = -1;
                            break;
                        case "after":
                            op = 1;
                            break;
                        case "at":
                        case "during":
                        case "with":
                        case "every":
                        default:
                            op = 0;
                            break;
                    }
                    String code = Util.getHL7EventTimingCode(op, meal);
                    int start = freqAttr.getBegin();
                    int end = freqAttr.getEnd();
                    if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                        ret.setBegin(Math.min(ret.getBegin(), start));
                        ret.setEnd(Math.max(ret.getEnd(), end));
                    } else {
                        ret.setBegin(start);
                        ret.setEnd(end);
                    }
                    EventTiming event = new EventTiming(jCas);
                    event.setBegin(ret.getBegin());
                    event.setEnd(ret.getEnd());
                    event.setValue(code);
                    event.addToIndexes();
                    repeatObject.setWhen(event);
                    if (repeatObject.getFrequency() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequency(null);
                    }
                    if (repeatObject.getFrequencyMax() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequencyMax(null);
                    }
                    if (repeatObject.getPeriod() != null) {
                        repeatObject.setPeriod(null);
                    }
                    if (repeatObject.getPeriodMax() != null) {
                        repeatObject.setPeriodMax(null);
                    }
                    if (repeatObject.getPeriodUnit() != null) {
                        repeatObject.setPeriodUnit(null);
                    }
                    flag = true;
                }
                // Times of day
                m2 = RegexpStatements.TIME_OF_DAY.matcher(covered);
                if (m2.find()) {
                    int start = freqAttr.getBegin();
                    int end = freqAttr.getEnd();
                    if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                        ret.setBegin(Math.min(ret.getBegin(), start));
                        ret.setEnd(Math.max(ret.getEnd(), end));
                    } else {
                        ret.setBegin(start);
                        ret.setEnd(end);
                    }
                    EventTiming event = new EventTiming(jCas);
                    event.setBegin(ret.getBegin());
                    event.setEnd(ret.getEnd());
                    event.setValue(Util.getHL7EventTimingCode(0, m2.group(1)));
                    event.addToIndexes();
                    repeatObject.setWhen(event);
                    if (repeatObject.getFrequency() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequency(null);
                    }
                    if (repeatObject.getFrequencyMax() != null) { // Hard rule match and MutEx, assume when is correct
                        repeatObject.setFrequencyMax(null);
                    }
                    if (repeatObject.getPeriod() != null) {
                        repeatObject.setPeriod(null);
                    }
                    if (repeatObject.getPeriodMax() != null) {
                        repeatObject.setPeriodMax(null);
                    }
                    if (repeatObject.getPeriodUnit() != null) {
                        repeatObject.setPeriodUnit(null);
                    }
                    flag = true;
                }
                // - No special cases
                if (!flag) {
                    ret.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, freqAttr.getCoveredText(), freqAttr.getBegin(), freqAttr.getEnd()));
                }
            }
        } else {
            // No frequency attribute, try to find in MedTime TODO duplicate code, cleanup
            for (Sentence s : drugToSentenceLookup.get(drug)) { // Should only be one
                for (MedTimex3 time : sentenceToMedTimeLookup.get(s)) {
                    String timexValue = time.getTimexValue();
                    String timeXType = time.getTimexType();
                    if (timeXType != null && timeXType.equalsIgnoreCase("SET")) {
                        Double[] timestamp = convertFromISO8601Standard(timexValue);
                        if (timestamp == null) continue;
                        ret.setBegin(time.getBegin());
                        ret.setEnd(time.getEnd());
                        String[] parsed = fromConvertedISO8601(timestamp);
                        if (Double.valueOf(Util.normalizeNumber(parsed[0])) % 24 == 0 && parsed[1].equalsIgnoreCase("h")) {
                            // Common pattern in MedTime
                            repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Double.valueOf(Util.normalizeNumber(parsed[0]))/24 + "", time.getBegin(), time.getEnd()));
                            repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, Util.transformUnitOfTime("day"), time.getBegin(), time.getEnd()));
                        } else {
                            repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Util.normalizeNumber(parsed[0]), time.getBegin(), time.getEnd()));
                            repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, parsed[1], time.getBegin(), time.getEnd()));
                        }
                        // Check for special cases via rule based methods TODO cleanup/abstractify
                        String covered = time.getCoveredText().toLowerCase().replaceAll("-", " ").replaceAll(" {2}", " ");
                        if (covered.contains("as needed") || covered.contains("prn")) { // As needed
                            int start = covered.indexOf("as needed");
                            int end;
                            if (start != -1) {
                                start += freqAttr.getBegin();
                                end = start + 9;
                            } else {
                                start = freqAttr.getBegin() + covered.indexOf("prn");
                                end = start + 3;
                            }
                            ret.setAsNeededBoolean(Util.instantiatePrimitiveWithValue(Boolean.class, jCas, true + "", start, end));
                            if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                                ret.setBegin(Math.min(ret.getBegin(), start));
                                ret.setEnd(Math.max(ret.getEnd(), end));
                            } else {
                                ret.setBegin(start);
                                ret.setEnd(end);
                            }
                        }
                        // on weekday1, weekday2, weekday3
                        Collection<Sentence> coveringSents = attributeToSentenceLookup.getOrDefault(freqAttr, new LinkedList<>());
                        for (Sentence sentence : coveringSents) {
                            covered = sentence.getCoveredText().toLowerCase().replaceAll("-", " ").replaceAll(" {2}", " ");
                            Matcher m2 = RegexpStatements.PERIOD_WEEKDAYS.matcher(covered);
                            if (m2.find()) {
                                int start = sentence.getBegin() + m2.toMatchResult().start();
                                int end = sentence.getBegin() + m2.toMatchResult().end();
                                if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                                    ret.setBegin(Math.min(ret.getBegin(), start));
                                    ret.setEnd(Math.max(ret.getEnd(), end));
                                } else {
                                    ret.setBegin(start);
                                    ret.setEnd(end);
                                }
                                String other = m2.group(1);
                                String days = m2.group(2).trim().replaceAll(",", "").replace("and", "").replaceAll(" {2}", " ");
                                String[] daysParsed = days.split("[, -]");
                                int freq = daysParsed.length;
                                int period = (other == null) ? 1 : 2;
                                int existingFreq = repeatObject.getFrequency() != null ? java.lang.Integer.valueOf(repeatObject.getFrequency().getValue()) : 1;
                                int existingFreqUpper = repeatObject.getFrequencyMax() != null ? java.lang.Integer.valueOf(repeatObject.getFrequencyMax().getValue()) : 1;
                                freq *= existingFreq;
                                int freqMax = freq * existingFreqUpper;
                                repeatObject.setPeriod(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, period + "", ret.getBegin(), ret.getEnd()));
                                repeatObject.setPeriodUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, "wk", ret.getBegin(), ret.getEnd()));
                                repeatObject.setFrequency(Util.instantiatePrimitiveWithValue(Integer.class, jCas, freq + "", ret.getBegin(), ret.getEnd()));
                                repeatObject.setFrequencyMax(Util.instantiatePrimitiveWithValue(Integer.class, jCas, freqMax + "", ret.getBegin(), ret.getEnd()));
                                FSArray weekdayArray = new FSArray(jCas, daysParsed.length);
                                weekdayArray.addToIndexes();
                                repeatObject.setDayOfWeek(weekdayArray);
                                for (int i = 0; i < daysParsed.length; i++) {
                                    Code dayCode = new Code(jCas);
                                    dayCode.setBegin(sentence.getBegin() + m2.start(2));
                                    dayCode.setEnd(sentence.getBegin() + m2.end(2));
                                    Matcher m3 = RegexpStatements.WEEKDAY_PARSER.matcher(daysParsed[i].toLowerCase());
                                    if (!m3.find()) {
                                        // Guaranteed true
                                        continue;
                                    }
                                    dayCode.setValue(m3.group(1).substring(0, 3));
                                    dayCode.addToIndexes();
                                    repeatObject.setDayOfWeek(i, dayCode);
                                }
                            }
                        }
                        // Meals
                        Matcher m2 = RegexpStatements.TIMING_EVENTS.matcher(covered);
                        if (m2.find()) {
                            String operator = m2.group(1);
                            String meal = m2.group(2);
                            int op;
                            switch (operator) {
                                case "before":
                                    op = -1;
                                    break;
                                case "after":
                                    op = 1;
                                    break;
                                case "at":
                                case "during":
                                case "with":
                                case "every":
                                case "on":
                                default:
                                    op = 0;
                                    break;
                            }
                            String code = Util.getHL7EventTimingCode(op, meal);
                            int start = time.getBegin();
                            int end = time.getEnd();
                            if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                                ret.setBegin(Math.min(ret.getBegin(), start));
                                ret.setEnd(Math.max(ret.getEnd(), end));
                            } else {
                                ret.setBegin(start);
                                ret.setEnd(end);
                            }
                            EventTiming event = new EventTiming(jCas);
                            event.setBegin(ret.getBegin());
                            event.setEnd(ret.getEnd());
                            event.setValue(code);
                            event.addToIndexes();
                            repeatObject.setWhen(event);
                            if (repeatObject.getFrequency() != null) { // Hard rule match and MutEx, assume when is correct
                                repeatObject.setFrequency(null);
                            }
                            if (repeatObject.getFrequencyMax() != null) { // Hard rule match and MutEx, assume when is correct
                                repeatObject.setFrequencyMax(null);
                            }
                            if (repeatObject.getPeriod() != null) {
                                repeatObject.setPeriod(null);
                            }
                            if (repeatObject.getPeriodMax() != null) {
                                repeatObject.setPeriodMax(null);
                            }
                            if (repeatObject.getPeriodUnit() != null) {
                                repeatObject.setPeriodUnit(null);
                            }
                        }
                        // Times of day
                        m2 = RegexpStatements.TIME_OF_DAY.matcher(covered);
                        if (m2.find()) {
                            if (ret.getBegin() != 0 || ret.getEnd() != 0) {
                                ret.setBegin(Math.min(ret.getBegin(), time.getBegin()));
                                ret.setEnd(Math.max(ret.getEnd(), time.getEnd()));
                            } else {
                                ret.setBegin(time.getBegin());
                                ret.setEnd(time.getEnd());
                            }
                            EventTiming event = new EventTiming(jCas);
                            event.setBegin(ret.getBegin());
                            event.setEnd(ret.getEnd());
                            event.setValue(Util.getHL7EventTimingCode(0, m2.group(1)));
                            event.addToIndexes();
                            repeatObject.setWhen(event);
                            if (repeatObject.getFrequency() != null) { // Hard rule match and MutEx, assume when is correct
                                repeatObject.setFrequency(null);
                            }
                            if (repeatObject.getFrequencyMax() != null) { // Hard rule match and MutEx, assume when is correct
                                repeatObject.setFrequencyMax(null);
                            }
                            if (repeatObject.getPeriod() != null) {
                                repeatObject.setPeriod(null);
                            }
                            if (repeatObject.getPeriodMax() != null) {
                                repeatObject.setPeriodMax(null);
                            }
                            if (repeatObject.getPeriodUnit() != null) {
                                repeatObject.setPeriodUnit(null);
                            }
                        }
                        break;
                    }
                }
            }
        }
        // - Set duration information
        MedAttr durationAttr = getMedAttr("duration", drug.getAttrs());
        if (durationAttr != null) {
            if (ret.getBegin() > durationAttr.getBegin() || (ret.getBegin() == 0 && ret.getEnd() == 0)) {
                ret.setBegin(durationAttr.getBegin());
            }
            if (ret.getEnd() < durationAttr.getEnd() || (ret.getBegin() == 0 && ret.getEnd() == 0)) {
                ret.setEnd(durationAttr.getEnd());
            }
            try {
                String[] parsed = durationAttr.getCoveredText().split("[ -]"); // All supplied examples follow this format, but could be more comprehensive TODO
                Decimal dObj = Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Util.normalizeNumber(parsed[0]), durationAttr.getBegin(), durationAttr.getEnd());
                FHIRString unitString = Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, Util.transformUnitOfTime(parsed[1]), durationAttr.getBegin(), durationAttr.getEnd());
                if (freqAttr != null) { // If a frequency is present this is a bounded duration instead
                    Duration d = new Duration(jCas, durationAttr.getBegin(), durationAttr.getEnd());
                    d.setValue(dObj);
                    d.setUnit(unitString);
                    d.addToIndexes();
                    repeatObject.setBoundsDuration(d);
                } else {
                    repeatObject.setDuration(dObj); //TODO does not support ranges but it doesn't seem to be a valid input case
                    repeatObject.setDurationUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, Util.transformUnitOfTime(parsed[1]), durationAttr.getBegin(), durationAttr.getEnd()));
                }
            } catch (IndexOutOfBoundsException e) {
                // Unmatched - see if MedTime has an associated identification
                boolean flag = false;
                for (Sentence s : drugToSentenceLookup.get(drug)) { // Should only be one
                    for (MedTimex3 time : sentenceToMedTimeLookup.get(s)) {
                        String timexValue = time.getTimexValue();
                        String timeXType = time.getTimexType();
                        if (timeXType != null && timeXType.equalsIgnoreCase("DURATION")) {
                            Double[] timestamp = convertFromISO8601Standard(timexValue);
                            if (timestamp == null) continue;
                            // Found timestamp
                            Util.expand(ret, time.getBegin(), time.getEnd());
                            if (freqAttr != null) {// If a frequency is present this is a bounded duration instead
                                Duration d = new Duration(jCas, time.getBegin(), time.getEnd());
                                String[] parsed = fromConvertedISO8601(timestamp);
                                d.setValue(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, parsed[0], time.getBegin(), time.getEnd()));
                                d.setUnit(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, Util.transformUnitOfTime(parsed[1]), time.getBegin(), time.getEnd()));
                                d.addToIndexes();
                                repeatObject.setBoundsDuration(d);
                            } else {
                                String[] parsed = fromConvertedISO8601(timestamp);
                                repeatObject.setDuration(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, parsed[0], time.getBegin(), time.getEnd()));
                                repeatObject.setDurationUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, Util.transformUnitOfTime(parsed[1]), time.getBegin(), time.getEnd()));
                            }
                            flag = true;
                            break;
                        }
                    }
                }
                // not found in medtime
                if (!flag) {
                    FHIRString text = new FHIRString(jCas, durationAttr.getBegin(), durationAttr.getEnd());
                    // Don't do one-line instantiation here for legibility reasons
                    text.setValue(
                            ((ret.getText() != null && ret.getText().getValue() != null) ?
                                    ret.getText().getValue() + " " + durationAttr.getCoveredText() : durationAttr.getCoveredText())
                    );
                    text.addToIndexes();
                    ret.setText(text);
                    Util.expand(ret, durationAttr.getBegin(), durationAttr.getEnd());
                }

            }
        } else {
            // No duration attribute, check MedTime
            for (Sentence s : drugToSentenceLookup.get(drug)) { // Should only be one
                for (MedTimex3 time : sentenceToMedTimeLookup.get(s)) {
                    String timexValue = time.getTimexValue();
                    String timeXType = time.getTimexType();
                    if (timeXType != null && timeXType.equalsIgnoreCase("DURATION")) {
                        Double[] timestamp = convertFromISO8601Standard(timexValue);
                        if (timestamp == null) continue;
                        // Found timestamp
                        Util.expand(ret, time.getBegin(), time.getEnd());
                        if (freqAttr != null) {
                            Duration d = new Duration(jCas, time.getBegin(), time.getEnd());
                            String[] parsed = fromConvertedISO8601(timestamp);
                            d.setValue(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, parsed[0], time.getBegin(), time.getEnd()));
                            d.setUnit(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, parsed[1], time.getBegin(), time.getEnd()));
                            d.addToIndexes();
                            repeatObject.setBoundsDuration(d);
                        } else {
                            String[] parsed = fromConvertedISO8601(timestamp);
                            repeatObject.setDuration(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, parsed[0], time.getBegin(), time.getEnd()));
                            repeatObject.setDurationUnit(Util.instantiatePrimitiveWithValue(UnitsOfTime.class, jCas, Util.transformUnitOfTime(parsed[1]), time.getBegin(), time.getEnd()));
                        }
                        break;
                    }
                }
            }
        }
        // Post-process the Timing Repeat for Aggregate fields
        // - Timing code
        if (repeatObject.getFrequency() != null && repeatObject.getPeriod() != null && repeatObject.getPeriodUnit() != null) {
            double freq = java.lang.Double.valueOf(repeatObject.getFrequency().getValue());
            double period = java.lang.Double.valueOf(repeatObject.getPeriod().getValue());
            double periodH = Util.convertUCUMToHours(repeatObject.getPeriodUnit());
            TimingAbbreviation abbv = TimingAbbreviation.getByTiming(freq, 1, period * periodH); // Not 100% right, offset can be one or two (e.g. every other 3 hrs)
            if (abbv != null) {
                int start = Math.min(Math.min(repeatObject.getFrequency().getBegin(), repeatObject.getPeriod().getBegin()), repeatObject.getPeriodUnit().getBegin());
                int end = Math.max(Math.max(repeatObject.getFrequency().getEnd(), repeatObject.getPeriod().getEnd()), repeatObject.getPeriodUnit().getEnd());
                CodeableConcept c = new CodeableConcept(jCas, start, end);
                c.addToIndexes();
                c.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, abbv.name(), start, end));
                c.setCoding(new FSArray(jCas, 1));
                Coding coding = new Coding(jCas, start, end);
                coding.addToIndexes();
                c.setCoding(0, coding);
                coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://hl7.org/fhir/v3/GTSAbbreviation", start, end));
                coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, abbv.name(), start, end));
                timing.setCode(c);
            }
        }
        timing.setBegin(ret.getBegin());
        timing.setEnd(ret.getEnd());
        repeatObject.setBegin(timing.getBegin());
        repeatObject.setEnd(timing.getEnd());
        repeatObject.addToIndexes();
        timing.setRepeat(repeatObject);
        timing.addToIndexes();
        ret.setTiming(timing);
        // Populate Route
        MedAttr routeAttr = getMedAttr("route", drug.getAttrs());
        if (routeAttr != null) {
            CodeableConcept routeConcept = new CodeableConcept(jCas, routeAttr.getBegin(), routeAttr.getEnd());
            routeConcept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, routeAttr.getCoveredText(), routeAttr.getBegin(), routeAttr.getEnd()));
            routeConcept.addToIndexes();
            ret.setRoute(routeConcept);
            Util.expand(ret, routeAttr.getBegin(), routeAttr.getEnd());
        }
        // Populate dosage quantity TODO positional information not 100% right here - it is correct but could be more specific/narrow
        MedAttr dosageAttr = getMedAttr("dosage", drug.getAttrs());
        if (dosageAttr != null) {
            if (dosageAttr.getCoveredText().contains("-") || dosageAttr.getCoveredText().contains("to")) { // TODO Perhaps more elegant way of doing this?
                try {
                    String[] parsed = dosageAttr.getCoveredText().split("(-| to )");
                    String unit = null;
                    if (parsed[1].split("[- ]").length > 1) {
                        unit = parsed[1].split("[- ]")[1];
                        parsed[1] = parsed[1].split("[- ]")[0];
                    }
                    if (Double.valueOf(Util.normalizeNumber(parsed[0].trim())) > Double.valueOf(Util.normalizeNumber(parsed[1].trim()))) {
                        String temp = parsed[0];
                        parsed[0] = parsed[1];
                        parsed[1] = temp;
                    }
                    Range rangeObj = new Range(jCas, dosageAttr.getBegin(), dosageAttr.getEnd());
                    Quantity low = new Quantity(jCas, dosageAttr.getBegin(), dosageAttr.getEnd());

                    low.setValue(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Util.normalizeNumber(parsed[0].trim()), dosageAttr.getBegin(), dosageAttr.getEnd()));
                    if (unit != null) {
                        low.setUnit(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, unit, dosageAttr.getBegin(), dosageAttr.getEnd()));
                    }
                    low.addToIndexes();
                    Quantity high = new Quantity(jCas, dosageAttr.getBegin(), dosageAttr.getEnd());
                    high.setValue(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Util.normalizeNumber(parsed[1]), dosageAttr.getBegin(), dosageAttr.getEnd()));
                    if (unit != null) {
                        high.setUnit(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, unit, dosageAttr.getBegin(), dosageAttr.getEnd()));
                    }
                    high.addToIndexes();
                    rangeObj.setLow(low);
                    rangeObj.setHigh(high);
                    rangeObj.addToIndexes();
                    rangeObj.setBegin(dosageAttr.getBegin());
                    rangeObj.setEnd(dosageAttr.getEnd());
                    ret.setDoseRange(rangeObj);
                } catch (IndexOutOfBoundsException e) {
                    getLogger().warn("Could not process dosage range " + dosageAttr.getCoveredText());
                }
            } else {
                Quantity dosage = new Quantity(jCas, dosageAttr.getBegin(), dosageAttr.getEnd());
                String[] parse = dosageAttr.getCoveredText().split("[- ]");
                dosage.setValue(Util.instantiatePrimitiveWithValue(Decimal.class, jCas, Util.normalizeNumber(parse[0]), dosageAttr.getBegin(), dosageAttr.getEnd()));
                if (parse.length > 1) {
                    dosage.setUnit(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, parse[1], dosageAttr.getBegin(), dosageAttr.getEnd()));
                }
                dosage.addToIndexes();
                ret.setDoseSimpleQuantity(dosage);
            }
            Util.expand(ret, dosageAttr.getBegin(), dosageAttr.getEnd());
        }
        // Post-process dosage instructions for aggregate attributes
        // - Max dose per period - depends on period, dose strength
        if (ret.getTiming() != null && ret.getTiming().getRepeat() != null && ret.getTiming().getRepeat().getPeriod() != null) {
            if (ret.getDoseSimpleQuantity() != null) {
                Decimal periodToUse = ret.getTiming().getRepeat().getPeriodMax() == null ? ret.getTiming().getRepeat().getPeriod() : ret.getTiming().getRepeat().getPeriodMax();
                int begin = Math.min(ret.getDoseSimpleQuantity().getBegin(), periodToUse.getBegin());
                int end = Math.max(ret.getDoseSimpleQuantity().getEnd(), periodToUse.getEnd());
                Ratio r = new Ratio(jCas, begin, end);
                r.setNumerator(ret.getDoseSimpleQuantity());
                Quantity periodQuant = new Quantity(jCas, ret.getTiming().getRepeat().getPeriod().getBegin(), periodToUse.getEnd());
                periodQuant.setValue(periodToUse);
                UnitsOfTime uot = ret.getTiming().getRepeat().getPeriodUnit();
                if (uot != null) {
                    periodQuant.setUnit(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, uot.getValue(), uot.getBegin(), uot.getEnd()));
                }
                r.setDenominator(periodQuant);
                ret.setMaxDosePerPeriod(r);
            }
            if (ret.getDoseRange() != null) {
                Quantity dosageToUse = ret.getDoseRange().getHigh();
                Decimal periodToUse = ret.getTiming().getRepeat().getPeriodMax() == null ? ret.getTiming().getRepeat().getPeriod() : ret.getTiming().getRepeat().getPeriodMax();
                int begin = Math.min(dosageToUse.getBegin(), periodToUse.getBegin());
                int end = Math.max(dosageToUse.getEnd(), periodToUse.getEnd());
                Ratio r = new Ratio(jCas, begin, end);
                r.setNumerator(dosageToUse);
                Quantity periodQuant = new Quantity(jCas, ret.getTiming().getRepeat().getPeriod().getBegin(), periodToUse.getEnd());
                periodQuant.setValue(periodToUse);
                UnitsOfTime uot = ret.getTiming().getRepeat().getPeriodUnit();
                if (uot != null) {
                    periodQuant.setUnit(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, uot.getValue(), uot.getBegin(), uot.getEnd()));
                }
                r.setDenominator(periodQuant);
                ret.setMaxDosePerPeriod(r);
            }
        }
        ret.addToIndexes();
        return ret;
    }

    /**
     * Converts a String in ISO 8601 section 5.5.3.2 standard as is used by MedTime
     *
     * @param value the Value to convert
     * @return A 7 element double array denoting number of years, months, weeks, days, hours, minutes, seconds, or null if
     * value does not contain any datetime information
     */
    private Double[] convertFromISO8601Standard(String value) {
        Double[] ret = new Double[7];
        // Pattern includes weeks, not technically part of the standard but is present in MedXN Output TODO move to Regexpr class
        Pattern p = Pattern.compile("[rp]+(?:([.0-9]+)y)?(?:([.0-9]+)m)?(?:([.0-9]+)w)?(?:([.0-9]+)d)?t?(?:([.0-9]+)h)?(?:([.0-9]+)m)?(?:([.0-9]+)s)?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(value);
        if (m.find()) {
            for (int i = 0; i < 7; i++) { // Iterate through groups and add
                if (m.group(i + 1) != null) ret[i] = Double.valueOf(m.group(i + 1));
                else ret[i] = 0D;
            }
        }
        for (int i = 0; i < 7; i++) { // Ensure non-zero
            if (ret[i] != 0D) return ret;
        }
        return null;
    }

    /**
     * Converts output from {@link #convertFromISO8601Standard(String)} into a standard format
     *
     * @param converted Output from {@link #convertFromISO8601Standard(String)}
     * @return A 2 element string array with index 0 being numeric value and index 1 being time unit
     */
    private String[] fromConvertedISO8601(Double[] converted) {
        if (timestampHasMultipleUnits(converted)) {
            String[] ret = new String[2];
            ret[1] = convertToDays(converted) + "";
            ret[2] = "d";
            return ret;
        } else {
            return outputSingleUnitTimestamp(converted);
        }
    }

    /**
     * @param convertedResponse output from {@link #convertFromISO8601Standard(String)}
     * @return The number of days this timestamp represents (assumes 365 days a year, 30 days a month)
     */
    private double convertToDays(Double[] convertedResponse) {
        double sum = 0;
        sum += 365 * convertedResponse[0];
        sum += 30 * convertedResponse[1];
        sum += 7 * convertedResponse[2];
        sum += convertedResponse[3];
        sum += 1 / 24D * convertedResponse[4];
        sum += 1D / (24 * 60) * convertedResponse[5];
        sum += 1D / (24 * 60 * 60) * convertedResponse[6];
        return sum;
    }

    /**
     * Determines whether a timestamp has multiple units involved
     *
     * @param convertedResponse output from {@link #convertFromISO8601Standard(String)}
     * @return true if more than one unit involved in timestamp
     */
    private boolean timestampHasMultipleUnits(Double[] convertedResponse) {
        int sum = 0;
        for (double d : convertedResponse) {
            if (d != 0D) {
                sum++;
            }
        }
        return sum > 1;
    }

    /**
     * Converts a single unit timestamp into its value and unit representation.
     * Run only if {@link #timestampHasMultipleUnits(Double[])} returns true
     *
     * @param timeStamp output from {@link #convertFromISO8601Standard(String)}
     * @return a 2 element array with index 0 being numeric value and index 1 being the unit in FHIR standard
     */
    private String[] outputSingleUnitTimestamp(Double[] timeStamp) {
        String[] ret = new String[2];
        for (int i = 0; i < timeStamp.length; i++) {
            double d = timeStamp[i];
            if (d > 0D) {
                ret[0] = d + "";
                switch (i) {
                    case 0:
                        ret[1] = "a";
                        break;
                    case 1:
                        ret[1] = "mo";
                        break;
                    case 2:
                        ret[1] = "wk";
                        break;
                    case 3:
                        ret[1] = "d";
                        break;
                    case 4:
                        ret[1] = "h";
                        break;
                    case 5:
                        ret[1] = "min";
                        break;
                    case 6:
                        ret[1] = "s";
                        break;
                }
                break;
            }
        }
        return ret;
    }

    /**
     * Creates a FHIR Medication from an input MedXN Drug
     *
     * @param jCas jCAS from which to pull input data as well as output converted FHIR types
     * @param drug The MedXN representation of the drug mention to import to FHIR format
     * @return A FHIR representation of the input drug
     */
    private Medication importMedicationFHIRElement(JCas jCas, org.ohnlp.medxn.type.Drug drug) {
        Medication out = new Medication(jCas, drug.getBegin(), drug.getEnd());
        // Create relevant ingredient object
        // Populate Ingredient
        MedicationProduct product = new MedicationProduct(jCas, drug.getBegin(), drug.getEnd());
        // - Import from MedXN
        ConceptMention concept = drug.getName();
        MedicationIngredient ingredient = new MedicationIngredient(jCas, concept.getBegin(), concept.getEnd());
        String name = concept.getNormTarget();
        // - Populate Ingredient FHIR Concept
        CodeableConcept fhirConcept = new CodeableConcept(jCas, concept.getBegin(), concept.getEnd());
        FHIRString nameString = Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, name, concept.getBegin(), concept.getEnd());
        nameString.addToIndexes();
        FSArray coding = new FSArray(jCas, 1);
        coding.addToIndexes();
        Coding codingObj = new Coding(jCas, concept.getBegin(), concept.getEnd());
        Code code = new Code(jCas, drug.getBegin(), drug.getEnd());
        String cui = drug.getNormRxCui();
        if (cui == null) {
            cui = drug.getName().getSemGroup();
            if (cui != null) {
                cui = cui.split("::")[0];
            }
        }
        code.setValue(cui);
        code.addToIndexes();
        codingObj.setCode(code);
        codingObj.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://www.nlm.nih.gov/research/umls/rxnorm", concept.getBegin(), concept.getEnd()));
        codingObj.addToIndexes();
        fhirConcept.setText(nameString);
        fhirConcept.setCoding(coding);
        fhirConcept.setCoding(0, codingObj);
        fhirConcept.addToIndexes();
        ingredient.setItemCodeableConcept(fhirConcept);
        // - Also mirror codeable concept here
        out.setCode(fhirConcept);
        // - Populate Ingredient FHIR Strength
        MedAttr strengthAttr = getMedAttr("strength", drug.getAttrs());
        if (strengthAttr != null) {
            Ratio ratio = new Ratio(jCas, strengthAttr.getBegin(), strengthAttr.getEnd());
            Quantity numerator = new Quantity(jCas, strengthAttr.getBegin(), strengthAttr.getEnd());
            // Convert to decimal and units
            String strength = strengthAttr.getCoveredText();
            String[] parse = strength.split(" "); // TODO: naive split 250 mg ->[250,mg], use a better regex (perhaps greedy match?)
            if (parse.length == 1) {
                parse = parse[0].split("-"); // Do in separate step to avoid ranges
            }
            Decimal d = new Decimal(jCas, strengthAttr.getBegin(), strengthAttr.getEnd());
            d.setValue(parse[0]); // No normalizing strengths due to not being able to represent a strength max in cases of range
            d.addToIndexes();
            if (parse.length > 1) {
                FHIRString unit = new FHIRString(jCas, strengthAttr.getBegin(), strengthAttr.getEnd());
                unit.setValue(parse[1]);
                unit.addToIndexes();
                numerator.setUnit(unit);
            }
            numerator.setValue(d);
            numerator.addToIndexes();
            ratio.setNumerator(numerator);
            Quantity denominator = new Quantity(jCas, strengthAttr.getBegin(), strengthAttr.getEnd());
            d = new Decimal(jCas, strengthAttr.getBegin(), strengthAttr.getEnd());
            d.setValue("1"); // Information is not provided in extracted form at the moment
            denominator.setValue(d);
            denominator.addToIndexes();
            ratio.setDenominator(denominator);
            ratio.addToIndexes();
            ingredient.setAmount(ratio);
            product.setBegin(Math.min(product.getBegin(), ratio.getBegin()));
            product.setEnd(Math.max(product.getEnd(), ratio.getEnd()));
        }
        ingredient.addToIndexes();
        FSArray ingredientArr = new FSArray(jCas, 1);
        ingredientArr.addToIndexes();
        product.setIngredient(ingredientArr);
        product.setIngredient(0, ingredient);
        // Populate Form
        MedAttr formattr = getMedAttr("form", drug.getAttrs());
        if (formattr != null) {
            CodeableConcept formConcept = new CodeableConcept(jCas);
            formConcept.setBegin(formattr.getBegin());
            formConcept.setEnd(formattr.getEnd());
            FHIRString text = new FHIRString(jCas);
            text.setBegin(formattr.getBegin());
            text.setEnd(formattr.getEnd());
            text.setValue(formattr.getCoveredText());
            text.addToIndexes();
            formConcept.setText(text);
            formConcept.addToIndexes();
            product.setForm(formConcept);
            product.setBegin(Math.min(product.getBegin(), formattr.getBegin()));
            product.setEnd(Math.max(product.getEnd(), formattr.getEnd()));
        }
        product.addToIndexes();
        out.setProduct(product);
        out.addToIndexes();
        return out;
    }

    /**
     * @param tag The name of the MedAttr to retrieve
     * @param arr A FSArray from {@link Drug#getAttrs()}
     * @return The attribute if it exists, or null
     */
    private MedAttr getMedAttr(String tag, FSArray arr) {
        for (FeatureStructure f : arr.toArray()) {
            if (f instanceof MedAttr) {
                if (((MedAttr) f).getTag().equalsIgnoreCase(tag)) {
                    return (MedAttr) f;
                }
            }
        }
        return null;
    }

    /**
     * UIMAFIT required static method. Do not remove
     *
     * @return A descriptor for this analysis engine
     * @throws ResourceInitializationException Inherited exception
     */
    @SuppressWarnings("unused")
    public static AnalysisEngineDescription createAnnotatorDescription() throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(MedExtractorsToFHIRMedications.class);
    }

}
