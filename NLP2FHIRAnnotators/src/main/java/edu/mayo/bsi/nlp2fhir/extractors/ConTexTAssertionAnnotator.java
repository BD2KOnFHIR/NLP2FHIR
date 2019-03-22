package edu.mayo.bsi.nlp2fhir.extractors;

import edu.mayo.bsi.nlp2fhir.extractors.context.ConTexTSettings;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JFSIndexRepository;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

public class ConTexTAssertionAnnotator extends JCasAnnotator_ImplBase {

    private ConTexTSettings conText;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        InputStream contextResource = ConTexTAssertionAnnotator.class.getResourceAsStream("/edu/mayo/advance/context/context_rules.txt");
        try {
            //contextFile= aContext.getResourceFilePath(PARAM_CONTEXT_FILE);
            conText = new ConTexTSettings(contextResource);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        //iterate over sentence
        JFSIndexRepository indexes = jCas.getJFSIndexRepository();
        Iterator<?> sentItr = indexes.getAnnotationIndex(Sentence.type).iterator();
        int docLength = jCas.getDocumentText().length();
        while (sentItr.hasNext()) {
            Sentence sent = (Sentence) sentItr.next();
            int endSen = sent.getEnd() + 1 > docLength ? docLength : sent.getEnd() + 1;
            String senPlusOne = jCas.getDocumentText().substring(sent.getBegin(), endSen);
            Iterator<?> cmItr = indexes.getAnnotationIndex(EventMention.type).subiterator(sent);
            ArrayList<EventMention> nes = new ArrayList<EventMention>();
            while (cmItr.hasNext()) {
                EventMention cm = (EventMention) cmItr.next();
                nes.add(cm);
            }
            if (nes.size() == 0) continue;

            for (EventMention ne : nes) {
                String tagged = null;
                //modified by Sunghwan (06-17-2014) to solve the "post" context word at the end of the sentence (eg, Amputation. No)
                //won't work if negation word is in the different sentence
                tagged = conText.preProcessSentence(senPlusOne.toLowerCase() + " ", ne.getCoveredText().toLowerCase());
                //String tagged = conText.preProcessSentence(sent.getCoveredText().toLowerCase(), ne.getCoveredText().toLowerCase());
                if (tagged == null)
                    continue;

                //tokenizing the sentence in words
                String[] words = tagged.split("[,;\\s]+");

                String neg = conText.applyNegEx(words);
                String tmp = conText.applyTemporality(words);
                String subj = conText.applyExperiencer(words);
                //		System.out.println(" subj " + subj);
                if (neg.equals(ConTexTSettings.NegationContext.Negated.name())) {
                    ne.setPolarity(-1);
                } else if (neg.equals(ConTexTSettings.NegationContext.Possible.name())) {
                    ne.setUncertainty(1);
                } else if (neg.equals(ConTexTSettings.NegationContext.Affirmed.name())) {
                    ne.setPolarity(1);
                }
                if (tmp.equals(ConTexTSettings.TemporalityContext.Hypothetical.name())) {
                    ne.setUncertainty(1);
                } else if (tmp.equals(ConTexTSettings.TemporalityContext.Historical.name()) && !subj.equals("Patient")) {
                    ne.setHistoryOf(1);
                } else if (tmp.equals(ConTexTSettings.TemporalityContext.Historical.name())) {
                    ne.setHistoryOf(1);
                }
                ne.setSubject(subj);
            }
        }

    }
}
