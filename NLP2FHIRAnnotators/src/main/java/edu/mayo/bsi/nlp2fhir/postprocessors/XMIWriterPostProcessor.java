package edu.mayo.bsi.nlp2fhir.postprocessors;

import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.TypeSystemUtil;
import org.apache.uima.util.XMLSerializer;
import org.xml.sax.SAXException;

import java.io.*;

/**
 * Converts CAS to xml format and writes to disk
 */
public class XMIWriterPostProcessor extends JCasAnnotator_ImplBase {


    public static String PARAM_OUTPUT_DIR = "OUTPUT_DIR";
    @ConfigurationParameter(
            name = "OUTPUT_DIR"
    )
    private File outputDir;
    private File outputSubDir = null;
    private File typeSystemFile = null;
    private boolean typeSystemFlag = false;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        if (outputSubDir == null) outputSubDir = new File(outputDir, "xmi");
        if (!outputSubDir.exists()) {
            if (!outputSubDir.mkdirs()) {
                throw new ResourceInitializationException();
            }
        }
        typeSystemFile = new File(this.outputDir, "TypeSystem.xml");
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        DocumentID id = JCasUtil.selectSingle(jCas, DocumentID.class);
        File out = new File(outputSubDir, id.getDocumentID() + ".xmi");
        if (!out.getParentFile().exists()) {
            if (!out.getParentFile().mkdirs()) {
                throw new IllegalStateException("Could not make parent dir");
            }
        }
        // Write a type system descriptor if not already done
        if (!typeSystemFlag) {
            typeSystemFlag = true;
            OutputStream bufferedStream = null;
            try {
                bufferedStream = new BufferedOutputStream(new FileOutputStream(typeSystemFile));
                TypeSystemUtil.typeSystem2TypeSystemDescription(jCas.getTypeSystem()).toXML(bufferedStream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (bufferedStream != null) {
                    try {
                        bufferedStream.close();
                    } catch (IOException ignored) {}
                }
            }
        }
        FileOutputStream fs = null;
        try {
            fs = new FileOutputStream(out);
            XmiCasSerializer serializer = new XmiCasSerializer(jCas.getTypeSystem());
            XMLSerializer xmlSerializer = new XMLSerializer(fs, false);
            serializer.serialize(jCas.getCas(), xmlSerializer.getContentHandler());
        } catch (FileNotFoundException | SAXException e) {
            throw new AnalysisEngineProcessException(e);
        } finally {
            if (fs != null) {
                try {
                    fs.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
