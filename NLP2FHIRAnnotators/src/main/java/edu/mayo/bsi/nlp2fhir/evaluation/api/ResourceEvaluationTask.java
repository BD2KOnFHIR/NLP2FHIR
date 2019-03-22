package edu.mayo.bsi.nlp2fhir.evaluation.api;

import org.apache.uima.jcas.JCas;
import org.hl7.fhir.Resource;

import java.util.List;

/**
 * Performs an evaluation between two resources
 * @param <R> The resource being evaluated
 */
public interface ResourceEvaluationTask<R extends Resource> {
    /**
     * @return The class of the resource being evaluated
     */
    Class<R> getResourceClass();

    /**
     * @param goldView The CAS containing the gold standard resources
     * @param resultView The CAS containing the NLP produced resources
     */
    void evaluate(JCas goldView, JCas resultView);

    /**
     * @return A list of human readable strings denoting results, should be tab delimited in the form of [name] [precision] [recall] [f1-score]
     */
    List<String> generateResults();
}
