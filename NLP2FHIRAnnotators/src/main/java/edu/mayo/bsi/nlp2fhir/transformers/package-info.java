/**
 * UIMAfit annotators that perform some part of conversion.
 * <br><br>
 * Naming convention: {@code {SourceTypeSystem}ToFHIR{ConversionType}.java}<br>
 * e.g. MedExtractorsToFHIRMedications.java converts Medication annotations from the MedExtractor NLP family
 * (MedXN, MedTime) to FHIR data format
 */
package edu.mayo.bsi.nlp2fhir.transformers;