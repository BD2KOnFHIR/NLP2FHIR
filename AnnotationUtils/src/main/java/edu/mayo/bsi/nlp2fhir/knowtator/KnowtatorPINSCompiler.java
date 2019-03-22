package edu.mayo.bsi.nlp2fhir.knowtator;

import edu.mayo.bsi.nlp2fhir.knowtator.model.KnowtatorAnnotationDef;
import edu.mayo.bsi.nlp2fhir.knowtator.model.KnowtatorClassDef;
import edu.mayo.bsi.nlp2fhir.knowtator.model.KnowtatorSlotDef;
import edu.mayo.bsi.nlp2fhir.knowtator.model.KnowtatorAnnotationDef;
import edu.mayo.bsi.nlp2fhir.knowtator.model.KnowtatorClassDef;
import edu.mayo.bsi.nlp2fhir.knowtator.model.KnowtatorSlotDef;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports .pins files from Knowtator into memory
 */
public class KnowtatorPINSCompiler {
    public static Pattern KNOWTATOR_CLASS_MENTIONS = Pattern.compile(
            "\\((\\[[^ ]+])(?: +)?of(?: +)?knowtator\\+class\\+mention"
    );
    public static Pattern KNOWTATOR_ANNOTATION = Pattern.compile(
            "\\((\\[[^ ]+])(?: +)?of(?: +)?knowtator\\+annotation"
    );
    public static Pattern KNOWTATOR_SLOT = Pattern.compile(
            "\\((\\[[^ ]+])(?: +)?of(?: +)?knowtator\\+string\\+slot\\+mention"
    );

    /**
     * Imports a .pins file
     *
     * @param f A .pins file to read
     * @return A map of document ID -> knowtator annotation collection for that specific document
     * @throws IOException If file does not exist or is not accessible
     */
    public static Map<String, Collection<KnowtatorAnnotationDef>> importPINSFile(File f) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(f));
        String line;
        boolean isClass = false;
        boolean isAnnotation = false;
        boolean isSlot = false;
        Map<String, KnowtatorClassDef> classDefs = new HashMap<>();
        Map<String, KnowtatorAnnotationDef> annDefs = new HashMap<>();
        Map<String, KnowtatorSlotDef> slotDefs = new HashMap<>();

        List<String> queue = null;
        String activeID = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(";")) continue; // Ignore comment
            if (line.startsWith("([")) { // Start of a new object
                if (isClass) {
                    isClass = false;
                    classDefs.put(activeID, new KnowtatorClassDef(activeID, queue));
                } else if (isAnnotation) {
                    isAnnotation = false;
                    annDefs.put(activeID, new KnowtatorAnnotationDef(activeID, queue));
                } else if (isSlot) {
                    isAnnotation = false;
                    slotDefs.put(activeID, new KnowtatorSlotDef(activeID, queue));
                }
                Matcher m = KNOWTATOR_CLASS_MENTIONS.matcher(line);
                if (m.matches()) {
                    isClass = true;
                    activeID = m.group(1);
                    queue = new LinkedList<>();
                } else {
                    m = KNOWTATOR_ANNOTATION.matcher(line);
                    if (m.matches()) {
                        isAnnotation = true;
                        activeID = m.group(1);
                        queue = new LinkedList<>();
                    } else {
                        m = KNOWTATOR_SLOT.matcher(line);
                        if (m.matches()) {
                            isSlot = true;
                            activeID = m.group(1);
                            queue = new LinkedList<>();
                        }
                    }
                }

            }
            // Not start of new object, continue progress
            if (isClass || isAnnotation || isSlot) { // Is an active interest, so import the line
                if (line.contains("(k")) { // make sure it is not blank line
                    queue.add(line.trim());
                }
            }
        }
        // Purge queue if interested at end of file
        if (isClass) {
            classDefs.put(activeID, new KnowtatorClassDef(activeID, queue));
        } else if (isAnnotation) {
            annDefs.put(activeID, new KnowtatorAnnotationDef(activeID, queue));
        } else if (isSlot) {
            slotDefs.put(activeID, new KnowtatorSlotDef(activeID, queue));
        }

        for (KnowtatorClassDef def : classDefs.values()) {
            def.initSlotDef(slotDefs);
        }

        Map<String, Collection<KnowtatorAnnotationDef>> defsByDoc = new HashMap<>();
        for (KnowtatorAnnotationDef def : annDefs.values()) {
            def.initClassDef(classDefs);
            String docId = def.getDocumentID();
            if (!defsByDoc.containsKey(docId)) {
                defsByDoc.put(docId, new LinkedList<>());
            }
            defsByDoc.get(docId).add(def);
        }

        return defsByDoc;
    }
}
