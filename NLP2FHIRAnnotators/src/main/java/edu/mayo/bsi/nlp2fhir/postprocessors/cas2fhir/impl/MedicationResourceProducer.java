package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl;

import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.Cas2FHIRUtils;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducer;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.*;
import org.hl7.fhir.dstu3.model.Medication;

import java.util.LinkedList;
import java.util.List;

public class MedicationResourceProducer implements ResourceProducer<Medication> {
    @Override
    public List<Medication> produce(JCas cas) {
        List<Medication> ret = new LinkedList<>();
        Composition composition = JCasUtil.selectSingle(cas, Composition.class);
        String docID = composition.getTitle().getValue();
        for (org.hl7.fhir.Medication condition : JCasUtil.select(cas, org.hl7.fhir.Medication.class)) {
            ret.add(produce(docID, condition));
        }
        return ret;
    }

    @Override
    public Medication produce(String documentID, Annotation ann) {
        if (!(ann instanceof org.hl7.fhir.Medication)) {
            throw new IllegalArgumentException("Expected Medication, got a " + ann.getClass());
        }
        org.hl7.fhir.Medication m = (org.hl7.fhir.Medication) ann;
        Medication out = new Medication();
        out.setId("Medication/" + Cas2FHIRUtils.generateUIDforAnnotation(documentID, m));
        // - Codeable Concept
        if (m.getCode() != null) { // Should always be true
            out.setCode(Cas2FHIRUtils.codeableConceptFromCAS(m.getCode()));
        }
        // - Medication Product
        if (m.getProduct() != null) {
            org.hl7.fhir.dstu3.model.Medication.MedicationProductComponent mpc = new org.hl7.fhir.dstu3.model.Medication.MedicationProductComponent();
            // -- Form
            if (m.getProduct().getForm() != null) {
                org.hl7.fhir.dstu3.model.CodeableConcept outC = new org.hl7.fhir.dstu3.model.CodeableConcept();
                CodeableConcept formC = m.getProduct().getForm();
                outC.setText(formC.getText().getValue());
                mpc.setForm(outC);
            }
            // -- Ingredients
            LinkedList<org.hl7.fhir.dstu3.model.Medication.MedicationProductIngredientComponent> ingredients = new LinkedList<>();
            for (FeatureStructure fs : m.getProduct().getIngredient().toArray()) {
                MedicationIngredient mi = (MedicationIngredient)fs;
                // --- Codeable Concepts
                org.hl7.fhir.dstu3.model.Medication.MedicationProductIngredientComponent ingOut = new org.hl7.fhir.dstu3.model.Medication.MedicationProductIngredientComponent();
                CodeableConcept miC = mi.getItemCodeableConcept();
                org.hl7.fhir.dstu3.model.CodeableConcept outC = new org.hl7.fhir.dstu3.model.CodeableConcept();
                outC.setText(miC.getText().getValue());
                List<org.hl7.fhir.dstu3.model.Coding> outCodings = new LinkedList<>();
                if (miC.getCoding() != null) {
                    for (FeatureStructure fs2 : miC.getCoding().toArray()) {
                        Coding c = (Coding) fs2;
                        org.hl7.fhir.dstu3.model.Coding outCoding = new org.hl7.fhir.dstu3.model.Coding();
                        outCoding.setCode(c.getCode().getValue());
                        outCoding.setSystem(c.getSystem().getValue());
                        outCodings.add(outCoding);
                    }
                    outC.setCoding(outCodings);
                }
                ingOut.setItem(outC);
                // --- Amount
                if (mi.getAmount() != null) {
                    Ratio r = mi.getAmount();
                    org.hl7.fhir.dstu3.model.Ratio rOut = new org.hl7.fhir.dstu3.model.Ratio();
                    // ---- Numerator
                    if (r.getNumerator() != null) {
                        org.hl7.fhir.dstu3.model.Quantity qOut = new org.hl7.fhir.dstu3.model.Quantity();
                        try {
                            qOut.setValue(Double.valueOf(r.getNumerator().getValue().getValue()));
                            if (r.getNumerator().getUnit() != null) {
                                qOut.setUnit(r.getNumerator().getUnit().getValue());
                            }
                            rOut.setNumerator(qOut);
                        } catch (Exception ignored) {}
                    }
                    // ---- Denominator
                    Cas2FHIRUtils.ratioDenominatorCopyFromCASRatio(r, rOut);
                    ingOut.setAmount(rOut);
                }
                ingredients.add(ingOut);
            }
            mpc.setIngredient(ingredients);
            out.setProduct(mpc);
        }
        return out;
    }

}
