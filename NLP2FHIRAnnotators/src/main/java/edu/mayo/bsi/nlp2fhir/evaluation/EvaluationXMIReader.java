package edu.mayo.bsi.nlp2fhir.evaluation;

import com.googlecode.clearnlp.io.FileExtFilter;
import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.component.ViewCreatorAnnotator;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.*;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EvaluationXMIReader extends JCasCollectionReader_ImplBase {

    private File[] extractedFileSet;
    private Map<String, File> goldNameToFileMap;
    private int currIdx;

    /**
     * Name of configuration parameter that must be set to the path of a directory containing the extracted XMI
     * files.
     */
    public static final String PARAM_EXTRACTED_INPUT = "ExtractedInputDirectory";
    @ConfigurationParameter(
            name=PARAM_EXTRACTED_INPUT
    )
    private File extractedDir;
    /**
     * Name of configuration parameter that must be set to the path of a directory containing the gold standard XMI
     * files.
     */
    public static final String PARAM_GOLD_INPUT = "GoldInputDirectory";
    @ConfigurationParameter(
            name=PARAM_GOLD_INPUT
    )
    private File goldDir;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        extractedFileSet = extractedDir.listFiles(new FileExtFilter("xmi"));
        goldNameToFileMap = new HashMap<>();
        File[] goldFiles = goldDir.listFiles(new FileExtFilter("xmi"));
        if (goldFiles != null) {
            for (File f : goldFiles) {
                goldNameToFileMap.put(f.getName(), f);
            }
        }
    }

    @Override
    public void getNext(JCas jCas) throws IOException, CollectionException {
        File nextFile = extractedFileSet[currIdx++];
        File goldFile = goldNameToFileMap.get(nextFile.getName());
        if (goldFile == null) {
            UIMAFramework.getLogger(EvaluationXMIReader.class).log(Level.SEVERE, "No gold standard for " + nextFile.getName() + " found, results will be affected");
        }
        try (FileInputStream inputStream = new FileInputStream(nextFile)) {
            XmiCasDeserializer.deserialize(inputStream, jCas.getCas(), false);
        } catch (SAXException e) {
            throw new CollectionException(e);
        }
        if (goldFile != null) {
            try (FileInputStream goldInputStream = new FileInputStream(goldFile)) {
                JCas goldView = ViewCreatorAnnotator.createViewSafely(jCas, "Gold Standard");
                goldView.setDocumentText(jCas.getDocumentText());
                CAS goldLoadView = CasCreationUtils.createCas(Collections.singletonList(getMetaData()));
                XmiCasDeserializer.deserialize(goldInputStream, goldLoadView, false, null, 0);
                new CasCopier(goldLoadView, jCas.getCas()).copyCasView(goldLoadView, "Gold Standard", false);
            } catch (SAXException | AnalysisEngineProcessException | ResourceInitializationException e) {
                throw new CollectionException(e);
            }
        }
    }

    @Override
    public boolean hasNext() {
        return currIdx < extractedFileSet.length;
    }

    @Override
    public Progress[] getProgress() {
        return new Progress[]{new ProgressImpl(this.currIdx, this.extractedFileSet.length, "entities")};
    }
}
