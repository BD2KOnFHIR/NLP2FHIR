package edu.mayo.bsi.nlp2fhir.knowtator.model;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KnowtatorAnnotationDef {
    private static Pattern CLASSDEF = Pattern.compile("knowtator_annotated_mention (\\[[^]]+])");
    private static Pattern ANN_SPAN = Pattern.compile("knowtator_annotation_span \"([0-9]+)\\|([0-9]+)");
    private static Pattern DOC_TEXT = Pattern.compile("knowtator_annotation_text \"([^\"]+)");
    private static Pattern DOC_ID_PATTERN = Pattern.compile("knowtator_annotation_text_source \\[([^]]+)]");
    private final String annName;
    private String classDefID = null;
    private String documentText = null;
    private String documentID = null;
    private String classDef = null;
    private String standardValue = null;
    private int start = 0;
    private int end = 0;
    public KnowtatorAnnotationDef(String name, List<String> def) {
        this.annName = name;
        for (String s : def) {
            Matcher m;
            m = CLASSDEF.matcher(s);
            if (m.find()) {
                classDefID = m.group(1);
            }
            m = ANN_SPAN.matcher(s);
            if (m.find()) {
                start = Integer.valueOf(m.group(1));
                end = Integer.valueOf(m.group(2));
            }
            m = DOC_TEXT.matcher(s);
            if (m.find()) {
                documentText = m.group(1);
                continue;
            }
            m = DOC_ID_PATTERN.matcher(s);
            if (m.find()) {
                documentID = m.group(1);
            }
        }
    }

    public String getDocumentID() {
        return documentID;
    }

    public String getDocumentText() {
        return documentText;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public String initClassDef(Map<String, KnowtatorClassDef> definitions) {
        if (definitions.get(classDefID) == null) {
            return null;
        }
        this.classDef = definitions.get(classDefID).getClassPath();
        this.standardValue = definitions.get(classDefID).getStandardValue();
        return classDef;
    }

    public String getClassDef() {
        return classDef;
    }

    public String getStandardValue() {
        return standardValue;
    }
}
