package edu.mayo.bsi.nlp2fhir.preprocessors;

import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.hl7.fhir.Composition;
import org.hl7.fhir.FHIRString;
import org.ohnlp.medtagger.cr.FileSystemReader;
import org.ohnlp.typesystem.type.structured.Document;

import java.io.File;
import java.io.IOException;

/**
 * Performs essentially same function as FileSystemReader that it extends but also appends metadata so as to be
 * standards-compliant with FHIR
 */
public class FHIRFileSystemReader extends FileSystemReader {
    @Override
    public void getNext(CAS cas) throws IOException, CollectionException {
        super.getNext(cas);
        try {
            JCas jCas = cas.getJCas();
            File f = new File(JCasUtil.selectSingle(jCas, Document.class).getFileLoc());
            jCas.createView("UriView").setSofaDataURI(f.toURI().toString(), "text");
            Composition fhirDocRepresentaiton = new Composition(jCas, 0, jCas.getDocumentText().length());
            FHIRString string = new FHIRString(jCas, 0, jCas.getDocumentText().length());
            string.setValue(f.getName());
            string.addToIndexes();
            DocumentID docID = new DocumentID(jCas);
            docID.setDocumentID(f.getName());
            docID.addToIndexes();
            fhirDocRepresentaiton.setTitle(string);
            fhirDocRepresentaiton.addToIndexes();
        } catch (CASException e) {
            e.printStackTrace();
        }
    }
}
