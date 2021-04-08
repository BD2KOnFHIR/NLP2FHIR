package edu.mayo.bsi.nlp2fhir.transformers;

import edu.mayo.bsi.nlp.vts.SNOMEDCT;
import edu.mayo.bsi.nlp.vts.UMLS;
import edu.mayo.bsi.nlp2fhir.Util;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.EntityMention;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.*;

import java.sql.SQLException;
import java.util.Collection;

public class CTAKESGenericToFHIRObservation  extends JCasAnnotator_ImplBase {
    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        for (EntityMention mention : JCasUtil.select(jCas, EntityMention.class)) {
            Observation constructed = new Observation(jCas, mention.getBegin(), mention.getEnd());
            if (mention.getOntologyConceptArr() != null) {
                CodeableConcept concept = new CodeableConcept(jCas, mention.getBegin(), mention.getEnd());
                concept.setText(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, mention.getCoveredText(), mention.getBegin(), mention.getEnd()));
                FSArray conceptArr = mention.getOntologyConceptArr();
                for (int j = 0; j < conceptArr.size(); j++) {
                    FeatureStructure fs = conceptArr.get(j);
                    if (!(fs instanceof UmlsConcept)) {
                        throw new AssertionError("Ontology Concept not a UMLS Concept!");
                    }
                    UmlsConcept c = (UmlsConcept) fs;
                    String code = c.getCui() + "|" + c.getTui();
                    String term = c.getPreferredText();
                    if (code != null) {
                        concept.setCoding(new FSArray(jCas, 1));
                        Coding coding = new Coding(jCas, mention.getBegin(), mention.getEnd());
                        coding.addToIndexes();
                        coding.setSystem(Util.instantiatePrimitiveWithValue(Uri.class, jCas, "http://www.nlm.nih.gov/research/umls/", mention.getBegin(), mention.getEnd()));
                        coding.setCode(Util.instantiatePrimitiveWithValue(Code.class, jCas, code, mention.getBegin(), mention.getEnd()));
                        coding.setDisplay(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, term, mention.getBegin(), mention.getEnd()));
                        concept.setCoding(0, coding);
                        break;
                    }

                }
                concept.addToIndexes();
                constructed.setCode(concept);
                Util.expand(constructed, concept.getBegin(), concept.getEnd());
            }
            constructed.addToIndexes();
        }
    }
}
