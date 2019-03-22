package edu.mayo.bsi.nlp2fhir.postprocessors;

import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DocumentTextContentPostProcessor extends JCasAnnotator_ImplBase {
    @SuppressWarnings("WeakerAccess")
    @ConfigurationParameter(
            name = "OUTPUT_DIR"
    )
    public File outDir;

    public static final String PARAM_OUTPUT_DIR = "OUTPUT_DIR";

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
        try {
            FileWriter out = new FileWriter(new File(outDir, JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID()));
            out.write(cas.getDocumentText());
            out.flush();
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }
}
