package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl;

import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.Cas2FHIRUtils;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducer;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.*;
import org.hl7.fhir.CodeableConcept;
import org.hl7.fhir.Composition;
import org.hl7.fhir.DosageInstruction;
import org.hl7.fhir.Medication;
import org.hl7.fhir.Ratio;
import org.hl7.fhir.Timing;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.MedicationStatement;
import org.hl7.fhir.exceptions.FHIRException;
import org.ohnlp.typesystem.type.textspan.Sentence;

import java.lang.Integer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MedicationStatementResourceProducer implements ResourceProducer<MedicationStatement> {
    @Override
    public List<MedicationStatement> produce(JCas cas) {
        List<MedicationStatement> ret = new LinkedList<>();
        Composition composition = JCasUtil.selectSingle(cas, Composition.class);
        String docID = composition.getTitle().getValue();
        for (org.hl7.fhir.MedicationStatement condition : JCasUtil.select(cas, org.hl7.fhir.MedicationStatement.class)) {
            ret.add(produce(docID, condition));
        }
        return ret;
    }

    @Override
    public MedicationStatement produce(String documentID, Annotation ann) {
        if (!(ann instanceof org.hl7.fhir.MedicationStatement)) {
            throw new IllegalArgumentException("Expected Medication Statement, got a " + ann.getClass());
        }
        org.hl7.fhir.MedicationStatement ms = (org.hl7.fhir.MedicationStatement) ann;
        MedicationStatement out = new MedicationStatement();
        out.setIdentifier(Collections.singletonList(Cas2FHIRUtils.generateUIDIdentifer(Cas2FHIRUtils.generateUIDforAnnotation(documentID, ms))));
        out.setId("MedicationStatement/" + Cas2FHIRUtils.generateUIDforAnnotation(documentID, ms));
        // - Drug Codeable Concept
        if (ms.getMedicationCodeableConcept() != null) { // Should always be true
            out.setMedication(Cas2FHIRUtils.codeableConceptFromCAS(ms.getMedicationCodeableConcept()));
        }
        // - Effective Date Time
        if (ms.getEffectiveDateTime() != null) {
            StringType d = new StringType();
            d.setValue(ms.getEffectiveDateTime().getValue()); // TODO possibly need to standardize this
            // msOut.setEffective(d); TODO
        }
        // - Reason for Administration
        if (ms.getReasonForUseCodeableConcept() != null) {
            List<org.hl7.fhir.dstu3.model.CodeableConcept> concepts = new LinkedList<>();
            for (FeatureStructure f : ms.getReasonForUseCodeableConcept().toArray()) {
                concepts.add(Cas2FHIRUtils.codeableConceptFromCAS((CodeableConcept)f));
            }
            if (concepts.size() > 0) {
                out.setReasonForUseCodeableConcept(concepts);
            }
        }

        // - Dosage Information
        if (ms.getDosage() != null) {
            List<org.hl7.fhir.dstu3.model.DosageInstruction> instructions = new LinkedList<>();
            for (FeatureStructure fs : ms.getDosage().toArray()) {
                DosageInstruction di = (DosageInstruction) fs;
                org.hl7.fhir.dstu3.model.DosageInstruction diOut = new org.hl7.fhir.dstu3.model.DosageInstruction();
                // -- Timing
                Timing t = di.getTiming();
                org.hl7.fhir.dstu3.model.Timing tOut = new org.hl7.fhir.dstu3.model.Timing();
                // -- Timing Code
                if (t.getCode() != null) {
                    tOut.setCode(Cas2FHIRUtils.codeableConceptFromCAS(t.getCode()));
                }
                TimingRepeat tr = t.getRepeat();
                org.hl7.fhir.dstu3.model.Timing.TimingRepeatComponent repeatOut = new org.hl7.fhir.dstu3.model.Timing.TimingRepeatComponent();
                // --- Count
                if (tr.getCount() != null) {
                    repeatOut.setCount(Integer.valueOf(tr.getCount().getValue()));
                }
                if (tr.getCountMax() != null) {
                    repeatOut.setCountMax(Integer.valueOf(tr.getCountMax().getValue()));
                }
                // --- Duration
                if (tr.getDuration() != null) {
                    repeatOut.setDuration(Double.valueOf(tr.getDuration().getValue()));

                }
                if (tr.getDurationMax() != null) {
                    repeatOut.setDurationMax(Double.valueOf(tr.getDurationMax().getValue()));
                }
                if (tr.getDurationUnit() != null && tr.getDurationUnit().getValue() != null) { // If value is null, matching unit of time for detected unit was not found
                    try {
                        repeatOut.setDurationUnit(org.hl7.fhir.dstu3.model.Timing.UnitsOfTime.fromCode(tr.getDurationUnit().getValue().toLowerCase()));
                    } catch (FHIRException e) {
                        e.printStackTrace();
                    }
                }
                if (tr.getBoundsDuration() != null) {
                    org.hl7.fhir.dstu3.model.Duration dOut = new org.hl7.fhir.dstu3.model.Duration();
                    dOut.setValue(Double.valueOf(tr.getBoundsDuration().getValue().getValue()));
                    dOut.setCode(dOut.getValue() + "");
                    if (tr.getBoundsDuration().getUnit() != null) {
                        dOut.setUnit(tr.getBoundsDuration().getUnit().getValue());
                        dOut.setCode(dOut.getValue() + dOut.getUnit());
                        dOut.setSystem("http://unitsofmeasure.org");
                    }

                    repeatOut.setBounds(dOut);
                }
                // --- Frequency
                if (tr.getFrequency() != null) {
                    repeatOut.setFrequency(Integer.valueOf(tr.getFrequency().getValue()));
                }
                if (tr.getFrequencyMax() != null) {
                    repeatOut.setFrequencyMax(Integer.valueOf(tr.getFrequencyMax().getValue()));
                }
                // --- Period
                if (tr.getPeriod() != null) {
                    repeatOut.setPeriod(Double.valueOf(tr.getPeriod().getValue()));
                }
                if (tr.getPeriodMax() != null) {
                    repeatOut.setPeriodMax(Double.valueOf(tr.getPeriodMax().getValue()));
                }
                if (tr.getPeriodUnit() != null) {
                    try {
                        repeatOut.setPeriodUnit(org.hl7.fhir.dstu3.model.Timing.UnitsOfTime.fromCode(tr.getPeriodUnit().getValue()));
                    } catch (FHIRException e) {
                        e.printStackTrace();
                    }
                }
                // --- Day of Week
                if (tr.getDayOfWeek() != null) {
                    List<Enumeration<org.hl7.fhir.dstu3.model.Timing.DayOfWeek>> dayList = new LinkedList<>();
                    for (FeatureStructure fs2 : tr.getDayOfWeek().toArray()) {
                        try {
                            org.hl7.fhir.dstu3.model.Timing.DayOfWeekEnumFactory factory = new org.hl7.fhir.dstu3.model.Timing.DayOfWeekEnumFactory();
                            org.hl7.fhir.dstu3.model.Timing.DayOfWeek day = org.hl7.fhir.dstu3.model.Timing.DayOfWeek.fromCode(((Code)fs2).getValue());
                            dayList.add(new Enumeration<>(factory, day));
                        } catch (FHIRException e) {
                            e.printStackTrace();
                        }
                    }
                    repeatOut.setDayOfWeek(dayList);
                }
                // --- Time of day TODO not handled
                // --- When
                if (tr.getWhen() != null) {
                    String value = tr.getWhen().getValue();
                    try {
                        org.hl7.fhir.dstu3.model.Timing.EventTiming et = org.hl7.fhir.dstu3.model.Timing.EventTiming.fromCode(value);
                        repeatOut.setWhen(et);
                    } catch (FHIRException e) {
                        e.printStackTrace();
                    }
                }
                tOut.setRepeat(repeatOut);
                diOut.setTiming(tOut);
                // -- As needed
                if (di.getAsNeededBoolean() != null || di.getAsNeededCodeableConcept() != null) {
                    if (di.getAsNeededCodeableConcept() != null) { // Prioritize this first
                        diOut.setAsNeeded(Cas2FHIRUtils.codeableConceptFromCAS(di.getAsNeededCodeableConcept()));
                    } else {
                        diOut.setAsNeeded(new BooleanType(di.getAsNeededBoolean().getValue().toLowerCase()));
                    }
                }
                // -- Site
                if (di.getSite() != null) {
                    diOut.setSite(Cas2FHIRUtils.codeableConceptFromCAS(di.getSite()));
                }
                // -- Additional Instructions
                if (di.getAdditionalInstructions() != null) {
                    List<org.hl7.fhir.dstu3.model.CodeableConcept> concepts = new LinkedList<>();
                    for (FeatureStructure aIFS : di.getAdditionalInstructions().toArray()) {
                        concepts.add(Cas2FHIRUtils.codeableConceptFromCAS((CodeableConcept)aIFS));
                    }
                    diOut.setAdditionalInstructions(concepts);
                }
                // -- Method
                if (di.getMethod() != null) {
                    diOut.setMethod(Cas2FHIRUtils.codeableConceptFromCAS(di.getMethod()));
                }
                // -- Dosage Values
                if (di.getDoseSimpleQuantity() != null) {
                    SimpleQuantity quantityOut = new SimpleQuantity();
                    if (di.getDoseSimpleQuantity().getValue() != null) {
                        quantityOut.setValue(Double.valueOf(Util.normalizeNumber(di.getDoseSimpleQuantity().getValue().getValue())));
                    }
                    if (di.getDoseSimpleQuantity().getUnit() != null) {
                        quantityOut.setUnit(di.getDoseSimpleQuantity().getUnit().getValue());
                    } else {
                        for (Sentence s : JCasUtil.selectCovering(Sentence.class, ms)) { // TODO placeholder
                            for (Medication m : JCasUtil.selectCovered(Medication.class, s)) {
                                if (m.getProduct().getForm() != null) {
                                    quantityOut.setUnit(m.getProduct().getForm().getText().getValue());
                                }
                            }
                        }
                    }
                    diOut.setDose(quantityOut);
                }
                if (di.getDoseRange() != null) {
                    org.hl7.fhir.dstu3.model.Range rOut = new org.hl7.fhir.dstu3.model.Range();
                    if (di.getDoseRange().getLow() != null) {
                        SimpleQuantity val = new SimpleQuantity();
                        val.setValue(Double.valueOf(di.getDoseRange().getLow().getValue().getValue()));
                        rOut.setLow(val);
                    }
                    if (di.getDoseRange().getHigh() != null) {
                        SimpleQuantity val = new SimpleQuantity();
                        val.setValue(Double.valueOf(di.getDoseRange().getHigh().getValue().getValue()));
                        rOut.setHigh(val);
                    }
                    diOut.setDose(rOut);
                }
                // -- Route
                if (di.getRoute() != null) {
                    CodeableConcept fhirCodeableConcept = di.getRoute();
                    org.hl7.fhir.dstu3.model.CodeableConcept routeCodeableConcept = new org.hl7.fhir.dstu3.model.CodeableConcept();
                    routeCodeableConcept.setText(fhirCodeableConcept.getText().getValue());
                    di.setRoute(fhirCodeableConcept);
                }
                // -- Max dose per period
                if (di.getMaxDosePerPeriod() != null) {
                    Ratio r = di.getMaxDosePerPeriod();
                    org.hl7.fhir.dstu3.model.Ratio rOut = new org.hl7.fhir.dstu3.model.Ratio();
                    // ---- Numerator
                    if (r.getNumerator() != null) {
                        org.hl7.fhir.dstu3.model.Quantity qOut = new org.hl7.fhir.dstu3.model.Quantity();
                        try {
                            qOut.setValue(Double.valueOf(r.getNumerator().getValue().getValue()));
                            if (r.getNumerator().getUnit() != null) {
                                qOut.setUnit(r.getNumerator().getUnit().getValue());
                            } else {
                                for (Sentence s : JCasUtil.selectCovering(Sentence.class, ms)) { // TODO placeholder
                                    for (Medication m : JCasUtil.selectCovered(Medication.class, s)) {
                                        if (m.getProduct().getForm() != null) {
                                            qOut.setUnit(m.getProduct().getForm().getText().getValue());
                                        }
                                    }
                                }
                            }
                            rOut.setNumerator(qOut);
                        } catch (Exception ignored) {}
                    }
                    // ---- Denominator
                    Cas2FHIRUtils.ratioDenominatorCopyFromCASRatio(r, rOut);
                    diOut.setMaxDosePerPeriod(rOut);
                }
                instructions.add(diOut);
            }
            out.setDosage(instructions);
        }
        return out;
    }
}
