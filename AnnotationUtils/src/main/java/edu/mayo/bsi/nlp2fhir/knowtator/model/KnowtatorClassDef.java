package edu.mayo.bsi.nlp2fhir.knowtator.model;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A knowtator class definition read in from a .pins file
 */
public class KnowtatorClassDef {

    private static Pattern CLASS_SEARCH = Pattern.compile("knowtator_mention_class ([^)]+)");
    private static Pattern SLOT_MENTION = Pattern.compile("knowtator_slot_mention ([^)]+)");
    private final String defName;
    private String slot = null;
    private String standardValue = null;
    private String classPath = null;


    public KnowtatorClassDef(String name, List<String> def) {
        this.defName = name;
        for (String s : def) {
            Matcher m = CLASS_SEARCH.matcher(s);
            if (m.find()) {
                classPath = m.group(1);
            }
            m = SLOT_MENTION.matcher(s);
            if (m.find()) {
                slot = m.group(1);
            }
        }

    }

    public String initSlotDef(Map<String, KnowtatorSlotDef> definitions) {
        if (definitions.containsKey(slot)) this.standardValue = definitions.get(slot).getValue();
        return standardValue;
    }

    public String getStandardValue() {
        return standardValue;
    }


    public String getClassPath() {
        return classPath;
    }
}
