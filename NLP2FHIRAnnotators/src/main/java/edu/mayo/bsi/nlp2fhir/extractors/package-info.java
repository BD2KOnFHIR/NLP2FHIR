/**
 * Performs extraction tasks not supported by any other NLP tool used as part of the pipeline.<br>
 * An assumption is made that these are run after (depend on the output of) all the transformers in the
 * {@link edu.mayo.bsi.nlp2fhir.transformers} package
 */
package edu.mayo.bsi.nlp2fhir.extractors;