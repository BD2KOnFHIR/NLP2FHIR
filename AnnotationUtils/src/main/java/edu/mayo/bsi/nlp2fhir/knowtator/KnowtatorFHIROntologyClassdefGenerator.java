package edu.mayo.bsi.nlp2fhir.knowtator;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import edu.mayo.bsi.nlp2fhir.common.FHIRJSONSchemaReader;
import edu.mayo.bsi.nlp2fhir.common.model.schema.FHIRSchema;
import edu.mayo.bsi.nlp2fhir.common.model.schema.SchemaResource;
import edu.stanford.smi.protege.model.*;
import edu.stanford.smi.protege.storage.clips.ClipsKnowledgeBaseFactory;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates a knowtator ontology using a FHIR JSON schema definition
 */
public class KnowtatorFHIROntologyClassdefGenerator {

    private static Cls PROPERTIES_CLS;
    private static Map<String, JsonObject> SCHEMAS = new HashMap<>();
    private static Set<String> types = new HashSet<>();
    private static HashSet<String> topLevelNames = new HashSet<>();
    private static KnowledgeBase MODEL;

    private static LinkedList<ConstrainJob> CONSTRAIN_JOBS = new LinkedList<ConstrainJob>();
    private static Cls EXTENSION_CLS;


    public static void main(String... args) {
        FHIRSchema schema = FHIRJSONSchemaReader.readSchema();
        for (SchemaResource res : schema.getTopLevelResources()) {
            topLevelNames.add(res.getName());
        }
        Collection errors = new ArrayList();
        ClipsKnowledgeBaseFactory factory = new ClipsKnowledgeBaseFactory();
        Project project = Project.createNewProject(factory, errors);
        handleErrors(errors);
        File protegeSchemaDir = new File("protege_fhir_schema");
        protegeSchemaDir.mkdirs();
        File knowtatorSchemaDir = new File("knowtator_fhir_schema");
        knowtatorSchemaDir.mkdirs();
        project.setProjectFilePath(new File(protegeSchemaDir, "FHIR_SCHEMA.pprj").getAbsolutePath());
        MODEL = project.getKnowledgeBase();
        SCHEMAS = schema.getJsonDefinitions();
        HashSet<String> touchedSchemas = getTouchedRootClses(new HashSet<>(), args);
        touchedSchemas.remove("Extension"); // We replace with our own definition
        EXTENSION_CLS = MODEL.createCls("Extension", MODEL.getRootClses());
        Slot uriSlot = MODEL.createSlot("extension_uri");
        EXTENSION_CLS.addDirectTemplateSlot(uriSlot);
        Slot instanceSlot = MODEL.createSlot("extension_value_instance");
        instanceSlot.setValueType(ValueType.INSTANCE);
        EXTENSION_CLS.addDirectTemplateSlot(instanceSlot);
        EXTENSION_CLS.addDirectTemplateSlot(MODEL.createSlot("extension_value_text"));
        PROPERTIES_CLS = MODEL.createCls("FHIRProperty", MODEL.getRootClses());
        Slot normalizeSlot = MODEL.createSlot("normalized_value");
        PROPERTIES_CLS.addDirectTemplateSlot(normalizeSlot);
        for (Map.Entry<String, JsonObject> e : SCHEMAS.entrySet()) {
            String item = e.getKey();
            item = e.getKey().substring(item.lastIndexOf("/") + 1);
            if (!e.getKey().substring(0, e.getKey().indexOf("#") - 12).equals(item)) {
                continue;
            }
            File out = new File(knowtatorSchemaDir, e.getKey().replaceAll("[#/\\\\]", "_") + ".pins_classdefs");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {
                LinkedList<String> stack = new LinkedList<>();
                LinkedList<String> lastRef = new LinkedList<>();
                stack.push(item);
                lastRef.push(item);
                Set<String> generatedClasses = new HashSet<>();
                if (touchedSchemas.size() == 0 || !touchedSchemas.contains(item)) {
                    continue;
                }
                Cls c = MODEL.createCls(item, MODEL.getRootClses());
                JsonArray arr = e.getValue().getAsJsonObject("definitions").getAsJsonObject(item).getAsJsonArray("allOf");
                String desc = null;
                for (JsonElement el : arr) {
                    if (el.getAsJsonObject().has("description")) {
                        desc = el.getAsJsonObject().get("description").getAsString();
                    }
                }
                if (desc != null) {
                    c.setDocumentation(desc);
                }
                process(e.getValue(), stack, lastRef, new HashSet<>(stack), generatedClasses, c);
                for (String classname : generatedClasses) {
                    writer.write(classname);
                    writer.newLine();
                }
                generatedClasses.clear();
                writer.flush();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        for (ConstrainJob j : CONSTRAIN_JOBS) {
            j.run(MODEL);
        }
        errors.clear();
        project.save(errors);
        handleErrors(errors);
    }

    private static HashSet<String> getTouchedRootClses(HashSet<String> curr, String... clses) {
        for (String cls : clses) {
            String schemaPth = cls + ".schema.json#/definitions/" + cls;
            if (!SCHEMAS.containsKey(schemaPth)) {
                if (SCHEMAS.containsKey(cls)) {
                    schemaPth = cls;
                    curr.add(cls);
                    cls = cls.substring(cls.lastIndexOf("/") + 1);
                } else {
                    Logger.getLogger("KnowtatorGenerator").log(Level.WARNING, "No class " + schemaPth);
                    continue;
                }
            } else {
                curr.add(cls);
            }
            JsonObject obj = SCHEMAS.get(schemaPth);
            JsonObject definitions = obj.getAsJsonObject("definitions");
            JsonObject classDef = definitions.getAsJsonObject(cls);
            if (!classDef.has("allOf")) {
                continue;
            }
            JsonArray elements = classDef.getAsJsonArray("allOf");
            for (JsonElement element : elements) {
                JsonObject eObj = element.getAsJsonObject();
                // We fake inheritance by copying fields, not by truly subclassing, so skip $ref
                if (eObj.has("$ref")) {
                    continue;
                }
                JsonObject fields = eObj.getAsJsonObject("properties");
                for (Map.Entry<String, JsonElement> fieldEntry : fields.entrySet()) {
                    if (fieldEntry.getKey().startsWith("_")) { // Skip extensions
                        continue;
                    }
                    String name = fieldEntry.getKey();
                    JsonObject fieldDef = fields.getAsJsonObject(name);
                    if (fieldDef.has("$ref")) { // Links to a sub-type/is not terminal
                        String reference = fieldDef.get("$ref").getAsString();
                        if (!reference.startsWith("#")) { // Defined in different JSON, so treat as terminal
                           String className = reference.substring(0, reference.indexOf("#"));
                           if (!curr.contains(className.substring(0, className.length() - 12))) {
                               getTouchedRootClses(curr, className.substring(0, className.length() - 12));
                           }
                        } else {
                            getTouchedRootClses(curr, cls + ".schema.json" + reference);
                        }
                    } else {
                        String type = fieldDef.get("type").getAsString();
                        if (type.toLowerCase().equals("array")) { // Is an array
                            JsonObject itemsObj = fieldDef.getAsJsonObject("items");
                            if (itemsObj.has("$ref")) {
                                String reference = itemsObj.get("$ref").getAsString();
                                if (!reference.startsWith("#")) { // Defined in different JSON, so treat as terminal
                                    String className = reference.substring(0, reference.indexOf("#"));
                                    if (!curr.contains(className.substring(0, className.length() - 12))) {
                                        getTouchedRootClses(curr, className.substring(0, className.length() - 12));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return curr;
    }

    private static void handleErrors(Collection errors) {
        for (Object error : errors) {
            System.out.println("Error: " + error);
        }
        if (!errors.isEmpty()) {
            System.exit(-1);
        }
    }

    private static void process(JsonObject schemaDef, Deque<String> pathStack, Deque<String> lastRefClass, Set<String> coveredClasses, Set<String> classes, Cls currRootClass) {
        if (schemaDef == null) {
            return;
        }
        JsonObject definitions = schemaDef.getAsJsonObject("definitions");
        JsonObject classDef = definitions.getAsJsonObject(lastRefClass.getLast());
        JsonArray requiredElements = classDef.getAsJsonArray("allOf");
        for (JsonElement rElement : requiredElements) {
            JsonObject elementObj = rElement.getAsJsonObject();
            if (elementObj.has("$ref")) { // Is a subclass with no special members
                String reference = elementObj.get("$ref").getAsString();
                if (coveredClasses.contains(reference)) {
                    continue;
                }
                coveredClasses.add(reference);
                JsonObject ref = SCHEMAS.get(reference);
                if (ref == null && reference.startsWith("#")) {
                    ref = schemaDef;
                }
                if (!reference.startsWith("Element") && !reference.startsWith("DomainResource") && !reference.startsWith("Meta") && !reference.startsWith("Resource")) { // Filter out meta that we don't really need
                    String item = reference.substring(reference.lastIndexOf("/") + 1);
                    lastRefClass.addLast(item);
                    process(ref, pathStack, lastRefClass, coveredClasses, classes, currRootClass);
                    lastRefClass.removeLast();
                }
                coveredClasses.remove(reference);
            } else {
                JsonObject fields = elementObj.getAsJsonObject("properties");
                for (Map.Entry<String, JsonElement> fieldEntry : fields.entrySet()) {
                    if (fieldEntry.getKey().startsWith("_")) { // Skip extensions
                        continue;
                    }
                    String name = fieldEntry.getKey();
                    pathStack.addLast(name);
                    JsonObject fieldDef = fields.getAsJsonObject(name);
                    if (fieldDef.has("$ref")) { // Links to a sub-type/is not terminal
                        String reference = fieldDef.get("$ref").getAsString();
                        if (!reference.startsWith("#")) { // Defined in different JSON, so treat as terminal
                            StringBuilder sB = new StringBuilder();
                            boolean flag = false;
                            for (String s : pathStack) {
                                if (flag) {
                                    sB.append(".");
                                } else {
                                    flag = true;
                                }
                                sB.append(s);
                            }
//                            Cls cls = MODEL.getCls(sB.toString());
//                                    if (cls == null) {
//                                        cls = MODEL.createCls(sB.toString(), Collections.singleton(currRootClass));
//                                        // Set documentation only if new so that we always get latest definition at top of stack
//                                        cls.setDocumentation(fieldDef.get("description").getAsString());
//                                        types.add(sB.toString());
//                                    }
//                                    Slot s = MODEL.getSlot("_target." + sB.toString());
                            Slot s = MODEL.getSlot(sB.toString());

                            if (s == null) {
//                                        s = MODEL.createSlot("_target." + sB.toString());
                                s = MODEL.createSlot("has_" + sB.toString());
                                s.setDocumentation("The annotation value of type " + reference.substring(reference.lastIndexOf("/") + 1) + " associated with this slot");
                                s.setValueType(ValueType.INSTANCE);
                            }
                            CONSTRAIN_JOBS.add(new ConstrainJob(sB.toString(), s, reference.substring(reference.lastIndexOf("/") + 1)));
//                                    cls.addDirectTemplateSlot(s);
                            currRootClass.addDirectTemplateSlot(s);
                            classes.add(sB.toString());
                            types.add(sB.toString());
                            classes.add(sB.toString());
                        } else {
                            if (coveredClasses.contains(reference)) {
                                pathStack.removeLast();
                                continue;
                            }
                            coveredClasses.add(reference);
                            JsonObject ref = SCHEMAS.get(reference);
                            if (ref == null && reference.startsWith("#")) {
                                ref = schemaDef;
                            }
                            String item = reference.substring(reference.lastIndexOf("/") + 1);
                            lastRefClass.addLast(item);
                            process(ref, pathStack, lastRefClass, coveredClasses, classes, currRootClass);
                            lastRefClass.removeLast();
                            coveredClasses.remove(reference);
                        }
                    } else {
                        String type = fieldDef.get("type").getAsString();
                        if (type.toLowerCase().equals("array")) { // Is an array
                            JsonObject itemsObj = fieldDef.getAsJsonObject("items");
                            if (itemsObj.has("$ref")) {
                                String reference = itemsObj.get("$ref").getAsString();
                                if (!reference.startsWith("#")) { // Defined in different JSON, so treat as terminal
                                    StringBuilder sB = new StringBuilder();
                                    boolean flag = false;
                                    for (String s : pathStack) {
                                        if (flag) {
                                            sB.append(".");
                                        } else {
                                            flag = true;
                                        }
                                        sB.append(s);
                                    }
//                                    Cls cls = MODEL.getCls(sB.toString());
//                                    if (cls == null) {
//                                        cls = MODEL.createCls(sB.toString(), Collections.singleton(currRootClass));
//                                        // Set documentation only if new so that we always get latest definition at top of stack
//                                        cls.setDocumentation(fieldDef.get("description").getAsString());
//                                        types.add(sB.toString());
//                                    }
//                                    Slot s = MODEL.getSlot("_target." + sB.toString());
                                    Slot s = MODEL.getSlot(sB.toString());

                                    if (s == null) {
//                                        s = MODEL.createSlot("_target." + sB.toString());
                                        s = MODEL.createSlot("has_" + sB.toString());
                                        s.setDocumentation("The annotation value of type " + reference.substring(reference.lastIndexOf("/") + 1) + " associated with this slot");
                                        s.setValueType(ValueType.INSTANCE);
                                    }
                                    s.setAllowsMultipleValues(true);
                                    CONSTRAIN_JOBS.add(new ConstrainJob(sB.toString(), s, reference.substring(reference.lastIndexOf("/") + 1)));
//                                    cls.addDirectTemplateSlot(s);
                                    currRootClass.addDirectTemplateSlot(s);
                                    classes.add(sB.toString());
                                } else {
                                    if (coveredClasses.contains(reference)) {
                                        pathStack.removeLast();
                                        continue;
                                    }
                                    coveredClasses.add(reference);
                                    JsonObject ref = SCHEMAS.get(reference);
                                    if (ref == null && reference.startsWith("#")) {
                                        ref = schemaDef;
                                    }
                                    String item = reference.substring(reference.lastIndexOf("/") + 1);
                                    lastRefClass.addLast(item);
                                    process(ref, pathStack, lastRefClass, coveredClasses, classes, currRootClass);
                                    lastRefClass.removeLast();
                                    coveredClasses.remove(reference);
                                }
                            } else {
                                // No reference, is a collection of some terminal type
                                StringBuilder sB = new StringBuilder();
                                boolean flag = false;
                                for (String s : pathStack) {
                                    if (flag) {
                                        sB.append(".");
                                    } else {
                                        flag = true;
                                    }
                                    sB.append(s);
                                }
//                                Cls cls = MODEL.getCls(sB.toString());
//                                if (cls == null) {
//                                    cls = MODEL.createCls(sB.toString(), Collections.singleton(currRootClass));
//                                    // Set documentation only if new so that we always get latest definition at top of stack
//                                    cls.setDocumentation(fieldDef.get("description").getAsString());
//                                    types.add(sB.toString());
//                                }
//                                Slot s = MODEL.getSlot(sB.toString() + ".value");
//                                if (s == null) {
//                                    s = MODEL.createSlot(sB.toString() + ".value");
//                                    s.setDocumentation(fieldDef.get("description").getAsString());
//                                }
                                Slot s = MODEL.getSlot("has_" + sB.toString());
                                if (s == null) {
                                    s = MODEL.createSlot("has_" + sB.toString());
                                    s.setDocumentation(fieldDef.get("description").getAsString());
                                    s.setValueType(ValueType.INSTANCE);
                                    Cls fieldProperty = MODEL.getCls(sB.toString());
                                    if (fieldProperty == null) {
                                        fieldProperty = MODEL.createCls(sB.toString(), Collections.singleton(PROPERTIES_CLS));
//                                // Set documentation only if new so that we always get latest definition at top of stack
                                        fieldProperty.setDocumentation(fieldDef.get("description").getAsString());
                                        types.add(sB.toString());
                                    }
                                    s.setAllowedClses(Collections.singletonList(fieldProperty));
                                }
                                s.setAllowsMultipleValues(true);
//                                cls.addDirectTemplateSlot(s);
                                currRootClass.addDirectTemplateSlot(s);
                                classes.add(sB.toString());
                            }
                        } else {
                            StringBuilder sB = new StringBuilder();
                            boolean flag = false;
                            for (String s : pathStack) {
                                if (flag) {
                                    sB.append(".");
                                } else {
                                    flag = true;
                                }
                                sB.append(s);
                            }
//                            Cls cls = MODEL.getCls(sB.toString());
//                            if (cls == null) {
//                                cls = MODEL.createCls(sB.toString(), Collections.singleton(currRootClass));
//                                // Set documentation only if new so that we always get latest definition at top of stack
//                                cls.setDocumentation(fieldDef.get("description").getAsString());
//                                types.add(sB.toString());
//                            }
//                            Slot s = MODEL.getSlot(sB.toString() + ".value");
//                            if (s == null) {
//                                s = MODEL.createSlot(sB.toString() + ".value");
//                                s.setDocumentation(fieldDef.get("description").getAsString());
//                            }
//                            cls.addDirectTemplateSlot(s);
                            Slot s = MODEL.getSlot("has_" + sB.toString());
                            if (s == null) {
                                s = MODEL.createSlot("has_" + sB.toString());
                                s.setDocumentation(fieldDef.get("description").getAsString());
                                s.setValueType(ValueType.INSTANCE);
                                Cls fieldProperty = MODEL.getCls(sB.toString());
                                if (fieldProperty == null) {
                                    fieldProperty = MODEL.createCls(sB.toString(), Collections.singleton(PROPERTIES_CLS));
//                                // Set documentation only if new so that we always get latest definition at top of stack
                                    fieldProperty.setDocumentation(fieldDef.get("description").getAsString());
                                    types.add(sB.toString());
                                }
                                s.setAllowedClses(Collections.singletonList(fieldProperty));
                            }
//                                cls.addDirectTemplateSlot(s);
                            currRootClass.addDirectTemplateSlot(s);
                            classes.add(sB.toString());
                        }
                    }
                    pathStack.removeLast();
                }
            }
        }
    }

    private static class ConstrainJob {
        private Slot constrainSlot;
        private String constrainPath;
        private String CLS_NAME;

        public ConstrainJob(String constrainPath, Slot constrainSlot, String CLS_NAME) {
            this.constrainSlot = constrainSlot;
            this.constrainPath = constrainPath;
            this.CLS_NAME = CLS_NAME;
        }

        public void run(KnowledgeBase base) {
            if (base.getCls(CLS_NAME) == null) {
                return;
            }
            if (MODEL.getCls(constrainPath) != null) {
                HashSet currRootCls = new HashSet();
                currRootCls.addAll(MODEL.getCls(constrainPath).getSuperclasses());
                MODEL.deleteCls(MODEL.getCls(constrainPath));
                MODEL.createCls(constrainPath, currRootCls);
            } else {
                MODEL.createCls(constrainPath, Collections.singletonList(base.getCls(CLS_NAME)));
            }
            constrainSlot.setAllowedClses(Collections.singletonList(base.getCls(constrainPath)));
        }
    }
}
