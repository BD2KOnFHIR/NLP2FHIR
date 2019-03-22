package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.dstu3.model.Resource;

import java.util.List;

public interface ResourceProducer<T extends Resource> {
    // Produces all resources of type in the CAS
    List<T> produce(JCas cas);
    // Produces a T directly from the specified annotation
    T produce(String documentID, Annotation ann);
}
