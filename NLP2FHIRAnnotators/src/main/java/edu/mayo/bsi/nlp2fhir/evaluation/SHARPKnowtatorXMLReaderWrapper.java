package edu.mayo.bsi.nlp2fhir.evaluation;

import org.apache.ctakes.core.ae.SHARPKnowtatorXMLReader;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.net.URI;

/**
 * Supplies a wrapper around {@link SHARPKnowtatorXMLReader} such that CAS adds are added to a "GoldStandard" sub-view
 */
public class SHARPKnowtatorXMLReaderWrapper extends SHARPKnowtatorXMLReader {
//    @Override
//    public void process(JCas cas) throws AnalysisEngineProcessException {
//        DocumentID docIDann = JCasUtil.selectSingle(cas, DocumentID.class);
//        JCas view;
//        try {
//            view = cas.createView("Gold Standard");
//        } catch (CASException e) {
//            throw new AnalysisEngineProcessException(e);
//        }
//        view.setDocumentText(cas.getDocumentText());
//        docIDann.addToIndexes(view);
//        super.process(view);
//    }

    @Override
    protected URI getKnowtatorURI(JCas jCas) {
        File textURI = new File(this.getTextURI(jCas));
        String filename = textURI.getName();
        File xmlPath = new File(textURI.getParentFile().getParent(), "goldstandard/" + filename + ".knowtator.xml");
        return xmlPath.toURI();
    }
}
