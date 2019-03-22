package edu.mayo.bsi.nlp2fhir.preprocessors;

import org.apache.ctakes.core.cr.XmiCollectionReaderCtakes;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.hl7.fhir.Composition;
import org.hl7.fhir.FHIRString;
import org.ohnlp.typesystem.type.structured.Document;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class FHIRXMIFileSystemReader extends XmiCollectionReaderCtakes {
    @Override
    public void getNext(CAS cas) throws IOException, CollectionException {
        super.getNext(cas);
        try {
            JCas jCas = cas.getJCas();
            Composition fhirDocRepresentaiton = new Composition(jCas, 0, jCas.getDocumentText().length());
            FHIRString string = new FHIRString(jCas, 0, jCas.getDocumentText().length());
            File f;
            try {
                Field filesField = XmiCollectionReaderCtakes.class.getDeclaredField("mFiles");
                filesField.setAccessible(true);
                ArrayList<File> value = (ArrayList<File>) filesField.get(this);
                Field index = XmiCollectionReaderCtakes.class.getDeclaredField("mCurrentIndex");
                index.setAccessible(true);
                int idx = index.getInt(this) - 1;
                f = value.get(idx);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            DocumentID docID = new DocumentID(jCas);
            docID.setDocumentID(f.getName().substring(0, f.getName().length() - 4));
            docID.addToIndexes();
            string.setValue(f.getName());
            string.addToIndexes();
            fhirDocRepresentaiton.setTitle(string);
            fhirDocRepresentaiton.addToIndexes();
        } catch (CASException e) {
            e.printStackTrace();
        }
    }
}
