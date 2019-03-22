package edu.mayo.bsi.nlp2fhir.transformers;

import edu.mayo.bsi.nlp2fhir.RegexpStatements;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.DateTime;
import org.hl7.fhir.MedicationStatement;
import org.ohnlp.medtime.type.MedTimex3;
import org.ohnlp.typesystem.type.textspan.Paragraph;
import org.ohnlp.typesystem.type.textspan.Sentence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Annotates effective date time for FHIR MedicationStatements if present.<br><br>
 * Algorithm:<br>
 * - Identify sections and store as a paragraph<br>
 * - Identify if paragraph contains an effective as of statement via MedTime and regex<br>
 * - Annotate all MedicationStatements within said paragraph with the relevant time statement<br>
 */
public class MedTimeToFHIRMedications extends JCasAnnotator_ImplBase {
    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        // - Identify Paragraphs
        String documentContent = jCas.getDocumentText();
        Pattern dualLineSplitPattern = Pattern.compile("\\n\\n", Pattern.MULTILINE); // Multiple linebreaks denotes new sections TODO ignores first paragraph (should not matter but for ocmpleteness sake)
        Matcher m = dualLineSplitPattern.matcher(documentContent);
        while (m.find()) {
            Paragraph p = new Paragraph(jCas);
            p.setBegin(m.end());
            Matcher m2 = dualLineSplitPattern.matcher(documentContent);
            if (m2.find(m.end())) {
                p.setEnd(m2.start());
            } else {
                p.setEnd(documentContent.length());
            }
            p.addToIndexes();
        }
        // - Index Paragraph to MedicationStatements, MedTime to Sentence, MedTime to Paragraph
        Map<Paragraph, Collection<MedicationStatement>> paraToMS = JCasUtil.indexCovered(jCas, Paragraph.class, MedicationStatement.class);
        Map<MedTimex3, Collection<Sentence>> mtToSent = JCasUtil.indexCovering(jCas, MedTimex3.class, Sentence.class);
        Map<MedTimex3, Collection<Paragraph>> mtToPara = JCasUtil.indexCovering(jCas, MedTimex3.class, Paragraph.class);
        // - Look for appropriate MedTime annotation candidates
        for (MedTimex3 time : JCasUtil.select(jCas, MedTimex3.class)) {
            if (time.getTimexType().equalsIgnoreCase("TIME") || time.getTimexType().equalsIgnoreCase("DATE")) {
                // -- Check sentence for an effective as of statement
                for (Sentence s : mtToSent.getOrDefault(time, new ArrayList<>(0))) {
                    m = RegexpStatements.CURRENT_AS_OF.matcher(s.getCoveredText());
                    if (m.find()) {
                        // - Set all Medication Statements in the paragraph to have this effective time
                        Collection<Paragraph> paras = mtToPara.getOrDefault(time, new ArrayList<>(0));
                        for (Paragraph p : paras) { // Should only have one
                            for (MedicationStatement ms : paraToMS.getOrDefault(p, new ArrayList<>(0))) {
                                DateTime dt = new DateTime(jCas);
                                dt.setBegin(time.getBegin());
                                dt.setEnd(time.getEnd());
                                dt.setValue(time.getTimexValue());
                                dt.addToIndexes();
                                ms.setEffectiveDateTime(dt);
                            }
                        }


                    }
                }
            }
        }
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
