package edu.mayo.bsi.nlp2fhir.postprocessors;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.Boolean;
import org.hl7.fhir.*;
import org.hl7.fhir.Integer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Converts extracted FHIR structures back to knowtator annotations
 */
public class Document2KnowtatorPostProcessor extends JCasAnnotator_ImplBase {
    private static final DateFormat KNOWTATOR_DATE_FORMAT = new SimpleDateFormat("EEE MMM dd kk:mm:ss zzz YYYY");

    @ConfigurationParameter(
            name = "TEMP_DIR",
            mandatory = false,
            defaultValue = "temp"
    )
    private File tempDir;

    private static final String CLASS_DEF =
            "([fhir_annotation_Instance_%CLASSDEF_NUMBER%] of  knowtator+class+mention\n" +
                    "\n" +
                    "\t(knowtator_mention_annotation [fhir_annotation_Instance_%ANNDEF_NUMBER%])\n" +
                    "\t(knowtator_mention_class %CLASSDEF%)" +
                    "%SLOT_IF_PRESENT%" +
                    ")\n";
    private static final String ANN_DEF =
            "([fhir_annotation_Instance_%ANNDEF_NUMBER%] of  knowtator+annotation\n" +
                    "\n" +
                    "\t(knowtator_annotated_mention [fhir_annotation_Instance_%CLASSDEF_NUMBER%])\n" +
                    "\t(knowtator_annotation_annotator [+fhir_anno_Instance_1])\n" +
                    "\t(knowtator_annotation_creation_date \"%CURR_DATE_TIME%\")\n" +
                    "\t(knowtator_annotation_span \"%START%|%END%\")\n" +
                    "\t(knowtator_annotation_text \"%COVERED%\")\n" +
                    "\t(knowtator_annotation_text_source [%DOCUMENT_NAME%]))\n";
    private static final String SLOT_IF_PRESENT = "\n\t(knowtator_slot_mention [fhir_annotation_Instance_%SLOTDEF_NUMBER%])";
    private static final String SLOT_NOT_PRESENT = "";
    private static final String SLOT_DEF =
            "([fhir_annotation_Instance_%SLOTDEF_NUMBER%] of  knowtator+string+slot+mention\n" +
                    "\n" +
                    "\t(knowtator_mention_slot [%CLASSDEF%])\n" +
                    "\t(knowtator_mention_slot_value \"%NORMVALUE%\")\n" +
                    "\t(knowtator_mentioned_in [fhir_annotation_Instance_%CLASSDEF_NUMBER%]))\n";
    private static final String TEXT_REF = "([%DOCNAME%] of  file+text+source\n" +
            ")\n\n";

    private static AtomicLong INSTANCE_GENERATOR = new AtomicLong(10015);

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        tempDir.mkdirs();
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        String docID = JCasUtil.selectSingle(jCas, Composition.class).getTitle().getValue();
        File outFile = new File(tempDir, docID + ".tmp");
        try (FileWriter out = new FileWriter(outFile)) {
            out.write(TEXT_REF.replace("%DOCNAME%", docID));
            // Go through every MedicationStatement
            for (MedicationStatement ms : JCasUtil.select(jCas, MedicationStatement.class)) {
                // - Codeable Concept
                CodeableConcept medConcept = ms.getMedicationCodeableConcept();
                out.write(constructAnnotation("MedicationStatement.medicationCodeableConcept", docID, medConcept.getText().getValue(), medConcept));
                // - EffectiveDateTime
                DateTime dt = ms.getEffectiveDateTime();
                if (dt != null) {
                    out.write(constructAnnotation("MedicationStatement.effectiveDatetime", docID, dt.getValue(), dt));
                }
                // - Reason
                CodeableConcept reason = ms.getReasonForUseCodeableConcept() != null ? ms.getReasonForUseCodeableConcept(0) : null;
                if (reason != null) {
                    out.write(constructAnnotation("Dosage.reasonCode", docID, reason.getText().getValue(), reason));
                }
                DosageInstruction di = ms.getDosage() == null ? null : ms.getDosage(0);
                if (di != null) {
                    // -- Route
                    CodeableConcept route = di.getRoute();
                    if (route != null) {
                        out.write(constructAnnotation("Dosage.route", docID, route.getText().getValue(), route));
                    }
                    // -- As Needed
                    // --- As a concept
                    CodeableConcept neededReason = di.getAsNeededCodeableConcept();
                    if (neededReason != null) {
                        out.write(constructAnnotation("Dosage.asNeededCodeableConcept", docID, neededReason.getText().getValue(), neededReason));
                    }
                    // --- As a boolean
                    Boolean b = di.getAsNeededBoolean();
                    if (b != null) {
                        out.write(constructAnnotation("Dosage.asNeededBoolean", docID, b.getValue(), b));
                    }
                    // -- Site
                    CodeableConcept site = di.getSite();
                    if (site != null) {
                        out.write(constructAnnotation("Dosage.site", docID, site.getText().getValue(), site));
                    }
                    // -- Timing
                    // --- Timing Code
                    if (di.getTiming().getCode() != null) {
                        CodeableConcept timingCode = di.getTiming().getCode();
                        if (timingCode != null) {
                            out.write(constructAnnotation("Dosage.Timing.code", docID, timingCode.getText().getValue(), timingCode));
                        }
                    }
                    TimingRepeat tr = di.getTiming().getRepeat();
                    if (tr.getBegin() != 0 || tr.getEnd() != 0) { // TODO change timings to only be set when present
                        // --- Period and Unit
                        Decimal d = tr.getPeriod();
                        UnitsOfTime t = tr.getPeriodUnit();
                        if (d != null) {
                            out.write(constructAnnotation("Dosage.Timing.repeat.period%2Bunit", docID, d.getValue() + "," + (t == null ? "" : t.getValue()), tr));
                        }
                        d = tr.getPeriodMax();
                        if (d != null) {
                            out.write(constructAnnotation("Dosage.Timing.repeat.periodMax", docID, d.getValue(), d));
                        }
                        // --- Time of Day
                        // TODO not implemented
                        // --- Day of Week
                        FSArray dowArr = tr.getDayOfWeek();
                        if (dowArr != null) {
                            StringBuilder dow = new StringBuilder();
                            boolean flag = false;
                            for (FeatureStructure fs : dowArr.toArray()) {
                                if (flag) {
                                    dow.append(" ");
                                } else {
                                    flag = true;
                                }
                                dow.append(((Code) fs).getValue());
                            }
                            out.write(constructAnnotation("Dosage.Timing.repeat.dayofWeek", docID, dow.toString(), tr));
                        }
                        // --- Frequency
                        Integer f = tr.getFrequency();
                        if (f != null) {
                            out.write(constructAnnotation("Dosage.Timing.repeat.frequency", docID, f.getValue(), f));
                        }
                        // --- When
                        EventTiming event = tr.getWhen();
                        if (event != null) {
                            out.write(constructAnnotation("Dosage.Timing.repeat.when", docID, event.getValue(), event));
                        }
                        // --- Duration
                        Decimal du = tr.getDuration();
                        if (du != null) {
                            out.write(constructAnnotation("Dosage.Timing.repeat.duration", docID, du.getValue(), du));
                        }
                        UnitsOfTime u = tr.getDurationUnit();
                        if (u != null) {
                            out.write(constructAnnotation("Dosage.Timing.repeat.durationUnit", docID, u.getValue(), u));
                        }
                        Duration bd = tr.getBoundsDuration();
                        if (bd != null) {
                            out.write(constructAnnotation("Dosage.Timing.repeat.duration", docID, bd.getValue().getValue(), bd));
                            if (bd.getUnit() != null) {
                                out.write(constructAnnotation("Dosage.Timing.repeat.durationUnit", docID, bd.getUnit().getValue(), bd.getUnit()));
                            }
                        }
                    }
                    // -- Dosage Quantity
                    Quantity q = di.getDoseSimpleQuantity();
                    if (q != null) {
                        out.write(constructAnnotation("Dosage.dose.quantity.value", docID, q.getValue().getValue(), q));
                        if (q.getUnit() != null) {
                            out.write(constructAnnotation("Dosage.dose.quantity.unit", docID, q.getUnit().getValue(), q));
                        }
                    }

                    Range r = di.getDoseRange();
                    if (r != null) {
                        Quantity low = r.getLow();
                        if (low != null) {
                            out.write(constructAnnotation("Dosage.dose.quantity.range.low.value", docID, low.getValue().getValue(), low));
                            if (low.getUnit() != null) {
                                out.write(constructAnnotation("Dosage.dose.quantity.range.low.unit", docID, low.getUnit().getValue(), low));
                            }
                        }
                        Quantity high = r.getHigh();
                        if (high != null) {
                            out.write(constructAnnotation("Dosage.dose.quantity.range.high.value", docID, high.getValue().getValue(), high));
                            if (high.getUnit() != null) {
                                out.write(constructAnnotation("Dosage.dose.quantity.range.high.unit", docID, high.getUnit().getValue(), high));
                            }
                        }

                    }
                    // -- Additional Instructions
                    FSArray aInstructions = di.getAdditionalInstructions();
                    if (aInstructions != null) {
                        for (FeatureStructure fs : aInstructions.toArray()) {
                            CodeableConcept concept = (CodeableConcept)fs;
                            out.write(constructAnnotation("Dosage.additionalInstructions", docID, concept.getText().getValue(), concept));
                        }
                    }
                    // -- Method
                    CodeableConcept method = di.getMethod();
                    if (method != null) {
                        out.write(constructAnnotation("Dosage.method", docID, method.getText().getValue(), method));
                    }
                    // - Max dose per period
                    Ratio maxPeriod = di.getMaxDosePerPeriod();
                    if (maxPeriod != null) {
                        Quantity n = maxPeriod.getNumerator();
                        if (n != null) {
                            Decimal d = n.getValue();
                            out.write(constructAnnotation("Dosage.maxDosePerPeriod.numerator.quantity.value", docID, d.getValue(), n));
                            if (n.getUnit() != null) {
                                out.write(constructAnnotation("Dosage.maxDosePerPeriod.numerator.quantity.unit", docID, n.getUnit().getValue(), n));
                            }
                        }
                        Quantity de = maxPeriod.getDenominator();
                        if (de != null) {
                            Decimal d = de.getValue();
                            out.write(constructAnnotation("Dosage.maxDosePerPeriod.denumerator.quantity.value", docID, d.getValue(), de));
                            if (de.getUnit() != null) {
                                out.write(constructAnnotation("Dosage.maxDosePerPeriod.denumerator.quantity.unit", docID, de.getUnit().getValue(), de));
                            }
                        }
                    }
                }
            }
            // Go through every medication
            for (Medication m : JCasUtil.select(jCas, Medication.class)) {
                // - Form
                CodeableConcept form = m.getProduct().getForm();
                if (form != null) {
                    out.write(constructAnnotation("Medication.form", docID, form.getText().getValue(), form));
                }
                // - Strength/Amount
                MedicationIngredient ingredient = m.getProduct().getIngredient(0); // Guaranteed
                Ratio r = ingredient.getAmount();
                if (r != null) {
                    Quantity q = r.getNumerator();
                    if (q != null) {
                        Decimal d = q.getValue();
                        out.write(constructAnnotation("Medication.ingredient.amount.numerator.quantity.value", docID, d.getValue(), q));
                        if (q.getUnit() != null) {
                            out.write(constructAnnotation("Medication.ingredient.amount.numerator.quantity.unit", docID, q.getUnit().getValue(), q));
                        }
                    }
                    // TODO: denumerator not implemented
                }

            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String constructAnnotation(String type, String id, String fhirValue, Annotation fhirAnn) {
        // Metadata
        long classDefInstance = INSTANCE_GENERATOR.incrementAndGet();
        long annDefInstance = INSTANCE_GENERATOR.incrementAndGet();
        long slotDefInstance = -1;

        boolean slot = !fhirValue.equalsIgnoreCase(fhirAnn.getCoveredText());
        String classDef = CLASS_DEF.replace("%CLASSDEF_NUMBER%", classDefInstance + "")
                .replace("%ANNDEF_NUMBER%", annDefInstance + "")
                .replace("%CLASSDEF%", type)
//                .replace("%SLOT_IF_PRESENT%", slot ? SLOT_IF_PRESENT : SLOT_NOT_PRESENT);
                .replace("%SLOT_IF_PRESENT%", SLOT_NOT_PRESENT);

        if (slot) {
//            slotDefInstance = INSTANCE_GENERATOR.incrementAndGet();
//            classDef = classDef.replace("%SLOTDEF_NUMBER%", slotDefInstance + "");
        }
        String annDef = ANN_DEF.replace("%ANNDEF_NUMBER%", annDefInstance + "")
                .replace("%CLASSDEF_NUMBER%", classDefInstance + "")
                .replace("%CURR_DATE_TIME%", KNOWTATOR_DATE_FORMAT.format(new Date()))
                .replace("%START%", fhirAnn.getBegin() + "")
                .replace("%END%", fhirAnn.getEnd() + "")
                .replace("%COVERED%", URLEncoder.encode(fhirAnn.getCoveredText()
                        .replaceAll("[\\n\\r]", " ")
                        .replaceAll(" ", "safe12121space") // So that url encoder doesn't replace with +
                ).replace("safe12121space", " "))
                .replace("%DOCUMENT_NAME%", id);
        String slotDef = null;
        if (slot) {
//            slotDef = SLOT_DEF.replace("%SLOTDEF_NUMBER%", slotDefInstance + "")
//                    .replace("%CLASSDEF%", "_" + type)
//                    .replace("%NORMVALUE%", fhirValue)
//                    .replace("%CLASSDEF_NUMBER%", classDefInstance + "");
        }
        StringBuilder ret = new StringBuilder(classDef).append("\n").append(annDef).append("\n");
//        if (slot) {
//            ret.append(slotDef).append("\n");
//        }
        return ret.toString();
    }
}
