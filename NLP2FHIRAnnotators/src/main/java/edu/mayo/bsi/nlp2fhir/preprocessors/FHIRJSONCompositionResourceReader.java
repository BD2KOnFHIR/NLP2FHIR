package edu.mayo.bsi.nlp2fhir.preprocessors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import edu.mayo.bsi.nlp2fhir.Util;
import edu.mayo.bsi.nlp2fhir.nlp.Section;
import edu.mayo.bsi.nlp2fhir.nlp.metadata.CompositionResource;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.hl7.fhir.FHIRString;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.StringType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Reads in FHIR Composition resource JSONs from a parameter directory and prepares them for further processing
 */
public class FHIRJSONCompositionResourceReader extends JCasCollectionReader_ImplBase {

    /**
     * Name of configuration parameter that must be set to the path of a directory containing the XMI
     * files.
     */
    public static final String PARAM_INPUTDIR = "InputDirectory";

    @ConfigurationParameter(
            name=PARAM_INPUTDIR
    )
    private File inputDir;
    private File[] docs;
    private int currFileIdx;
    private FhirContext context = FhirContext.forDstu3();
    private IParser parser = context.newJsonParser();

    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        docs = inputDir.listFiles();
        currFileIdx = 0;
    }

    @Override
    public void getNext(JCas jCas) throws IOException {
        File currFile = docs[currFileIdx++];
        StringBuilder sB = new StringBuilder();
        for (String s : Files.readAllLines(currFile.toPath())) {
            sB.append(s); //JSON so we don't overly care about spacing
        }
        Composition curr = parser.parseResource(Composition.class, sB.toString());
        DocumentID id = new DocumentID(jCas);
        id.setDocumentID(curr.getId().split("/")[1]); // Composition resources are stored as "Composition/docId"
        id.addToIndexes();
        StringBuilder text = new StringBuilder();
        int secStart = 0;
        Collection<Section> sections = new ArrayList<>(curr.getSection().size());
        // Add sections
        for (Composition.SectionComponent section : curr.getSection()) {
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
        res.setSerialized(parser.encodeResourceToString(curr));
        res.addToIndexes();
        // TODO better to populate this instead of using modified output json above
        org.hl7.fhir.Composition composition = new org.hl7.fhir.Composition(jCas, 0, text.toString().length());
        composition.setTitle(Util.instantiatePrimitiveWithValue(FHIRString.class, jCas, id.getDocumentID(), composition.getBegin(), composition.getEnd()));
        composition.addToIndexes();
    }

    @Override
    public boolean hasNext()  {
        return currFileIdx < docs.length;
    }

    @Override
    public Progress[] getProgress() {
        return new Progress[] {new ProgressImpl(currFileIdx, docs.length, Progress.ENTITIES)};
    }
}
