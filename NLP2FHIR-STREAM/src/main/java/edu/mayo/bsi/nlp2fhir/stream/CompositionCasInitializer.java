package edu.mayo.bsi.nlp2fhir.stream;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.nlp.Section;
import edu.mayo.bsi.nlp2fhir.nlp.metadata.CompositionResource;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;
import org.hl7.fhir.FHIRString;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.StringType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class CompositionCasInitializer extends JCasAnnotator_ImplBase {

    private FhirContext context = FhirContext.forDstu3();
    private IParser parser = context.newJsonParser();

    @Override
    public void process(JCas cas) {
        Composition document = parser.parseResource(Composition.class, cas.getDocumentText());
        JCas jCas = null;
        try {
            jCas = cas.createView("text");
        } catch (CASException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        DocumentID id = new DocumentID(jCas);
        id.setDocumentID(UUID.randomUUID().toString()); // Composition resources are stored as "Composition/docId"
        id.addToIndexes();
        StringBuilder text = new StringBuilder();
        int secStart = 0;
        Collection<Section> sections = new ArrayList<>(document.getSection().size());
        // Add sections
        for (Composition.SectionComponent section : document.getSection()) {
            // - Add section text
            String secText = section.getText().getDiv().allText() + "\n";
            text.append(secText);
            // - Add section annotation
            int end = secStart + secText.length();
            Section sectionAnn = new Section(jCas, secStart, end);
            sectionAnn.setId(section.getCode().getCodingFirstRep().getCode());
            sectionAnn.setName(section.getCode().getCodingFirstRep().getDisplay());
            sections.add(sectionAnn);
            // Internal NLP2FHIR tracking
            UUID sectionUID = UUID.randomUUID();
            sectionAnn.setInternalId(sectionUID.toString());
            Extension trackingTempExtension = new Extension();
            trackingTempExtension.setUrl("temp:auto:nlp2fhir:id");
            trackingTempExtension.setValue(new StringType(sectionUID.toString()));
            section.addExtension(trackingTempExtension);
            secStart = end;
        }
        jCas.setDocumentText(text.toString());
        // Run after document text is set so we don't run into length/out of bounds issues
        for (Section s : sections) {
            s.addToIndexes();
        }
        // Track the modified output JSON
        CompositionResource res = new CompositionResource(jCas);
        res.setSerialized(parser.encodeResourceToString(document));
        res.addToIndexes();
        // TODO better to populate this instead of using modified output json above
        org.hl7.fhir.Composition composition = new org.hl7.fhir.Composition(jCas, 0, text.toString().length());
        composition.setTitle(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, id.getDocumentID(), composition.getBegin(), composition.getEnd()));
        composition.addToIndexes();
    }
}
