package edu.mayo.bsi.nlp2fhir.knowtator.model;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A knowtator slot definition as read from a .pins file */
public class KnowtatorSlotDef {
    private Pattern valuePattern = Pattern.compile("knowtator_mention_slot_value \"([^\"]+)");
    private String value = null;

    public KnowtatorSlotDef(String id, List<String> queue) {
        for (String s : queue) {
            Matcher m = valuePattern.matcher(s);
            if (m.find()) {
                value = m.group(1);
            }
        }
    }

    public String getValue() {
        return value;
    }
}
