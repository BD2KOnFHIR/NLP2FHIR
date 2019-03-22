package edu.mayo.bsi.nlp2fhir.extractors;

import edu.mayo.bsi.nlp2fhir.nlp.Section;
import org.apache.commons.io.FileUtils;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exceedingly simplistic section detector that simply looks at beginning of lines
 */
public class SectionExtractor extends JCasAnnotator_ImplBase {
    private Map<String, String> startToCodeMap;

    public static final String SECTION_DEFINITION_PARAM = "SECTION_DEFINITION";
    @ConfigurationParameter(
            name = "SECTION_DEFINITION",
            description = "A tab-separated section name -> LOINC code mapping"
    )
    private String sectionDef;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        try {
            startToCodeMap = new HashMap<>();
            List<String> entries = Files.readAllLines(new File((String) context.getConfigParameterValue(SECTION_DEFINITION_PARAM)).toPath());
            for (String entry : entries) {
                String[] parsed = entry.split("\t");
                startToCodeMap.put(parsed[0].toLowerCase(), parsed[1].trim());
            }
        } catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
    }

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
        String documentText = cas.getDocumentText();
        int currStart = 0;
        int currOffset = 0;
        String currSectionMap = null;
        String[] arr = documentText.split("\n");
        for (int i = 0 ; i < arr.length; i++) { // Split into newlines
            String s = arr[i];
            for (String text : startToCodeMap.keySet()) {
                if (s.trim().length() > 1) {
                    String firstchar = s.trim().substring(0, 2);
                    if (!firstchar.equals(firstchar.toUpperCase())) {
                        continue; // Skip uncapitalized headers TODO
                    }
                } else {
                    continue;
                }
                if (s.trim().toLowerCase().startsWith(text)) {
                    if (currSectionMap != null) {
                        Section sec = new Section(cas, currStart, Math.min(documentText.length(), currStart + currOffset));
                        sec.setId(currSectionMap);
                        sec.addToIndexes();
                    }
                    currStart = currStart + currOffset;
                    currOffset = 0;
                    currSectionMap = startToCodeMap.get(text);
                    break;
                }
            }
            currOffset += s.length();
            if (i < arr.length - 1) {
                currOffset += 1;
            }
        }
        if (currSectionMap != null) {
            Section sec = new Section(cas, currStart, documentText.length());
            sec.setId(currSectionMap);
            sec.addToIndexes();
        }
    }
}
