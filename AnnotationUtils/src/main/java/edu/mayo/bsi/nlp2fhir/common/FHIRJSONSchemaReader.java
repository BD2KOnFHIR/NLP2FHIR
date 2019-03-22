package edu.mayo.bsi.nlp2fhir.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.mayo.bsi.nlp2fhir.common.model.schema.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A shared class with the ability to read in a FHIR schema definition for parsing
 */
public class FHIRJSONSchemaReader {

    public static void main(String... args) {
        readSchema();
    }


    /**
     * Reads in a schema from a directory containing the schema's JSON definitions
     *
     * @return A {@link FHIRSchema} object representing the read-in schema
     */
    public static FHIRSchema readSchema() {
        Map<String, JsonObject> definitions = new HashMap<>();
        ZipInputStream zis = new ZipInputStream(FHIRJSONSchemaReader.class.getResourceAsStream("/edu/mayo/bsi/nlp2fhir/fhir/schema/dstu3.zip")); // TODO don't hardcode this
        ZipEntry entry;
        try {
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equalsIgnoreCase("fhir.schema.json") || name.equalsIgnoreCase("ResourceList.schema.json")) {
                    continue; // Skip over the root definition to prevent duplicates
                }
                String definition = IOUtils.toString(new BOMInputStream(zis));
                zis.closeEntry();
                JsonObject root = new JsonParser().parse(definition).getAsJsonObject();
                JsonObject definitionMap = root.getAsJsonObject("definitions");
                for (Map.Entry<String, JsonElement> e : definitionMap.entrySet()) {
                    definitions.put(name + "#/definitions/" + e.getKey(), root);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, SchemaResource> classdefToResourceMap = new HashMap<>(); // Stores internal reference to resources, used to ensure that a consistant resource map is shared across all
        for (Map.Entry<String, JsonObject> definitionEntry : definitions.entrySet()) {
            String item = definitionEntry.getKey();
            item = definitionEntry.getKey().substring(item.lastIndexOf("/") + 1);
            JsonObject classDef = definitionEntry.getValue().getAsJsonObject("definitions").getAsJsonObject(item);
            SchemaResource inherits = getInheritsReference(definitionEntry.getKey().substring(0, definitionEntry.getKey().indexOf("#")), classDef, classdefToResourceMap);
            String description = getResourceDescription(classDef);
            Map<String, PropertyDefinition> properties = parseProperties(definitionEntry.getKey().substring(0, definitionEntry.getKey().indexOf("#")), classDef, classdefToResourceMap);
            SchemaResource producedResource = classdefToResourceMap.computeIfAbsent(definitionEntry.getKey(), k -> new SchemaResource());
            producedResource.setName(item);
            producedResource.setDescription(description);
            producedResource.setDefinitions(properties);
            producedResource.setInherits(inherits);
            classdefToResourceMap.put(definitionEntry.getKey(), producedResource);
        }
        Map<String, SchemaResource> cleanedResourceMap = new HashMap<>();
        classdefToResourceMap.forEach((fullDef, value) -> {
            String root = fullDef.substring(0, fullDef.indexOf("#"));
            root = root.substring(0, root.length() - 12);
            String tail = fullDef.substring(fullDef.lastIndexOf("/") + 1);
            if (root.equalsIgnoreCase(tail)) {
                cleanedResourceMap.put(root.toLowerCase(), value);
            }
        });
        Collection<SchemaResource> topLevelResources = new HashSet<>(cleanedResourceMap.values());
        return new FHIRSchema(topLevelResources, cleanedResourceMap, definitions);
    }

    /**
     * @param definition A json definition of the resource
     * @return The resource's description
     */
    private static String getResourceDescription(JsonObject definition) {
        if (definition.has("allOf")) {
            for (JsonElement element : definition.getAsJsonArray("allOf")) {
                JsonObject property = element.getAsJsonObject();
                if (property.has("description")) {
                    return property.get("description").getAsString();
                }
            }
        }
        return null;
    }

    /**
     * @param rootSchema            The name of the file from which the definition is created
     * @param definition            The resource's description
     * @param classDefToResourceMap A global map of class definitions to resource
     * @return The resource that the supplied definition extends, if present. Null otherwise
     */
    private static SchemaResource getInheritsReference(String rootSchema, JsonObject definition, Map<String, SchemaResource> classDefToResourceMap) {
        if (definition.has("allOf")) {
            for (JsonElement element : definition.getAsJsonArray("allOf")) {
                JsonObject property = element.getAsJsonObject();
                if (property.has("$ref")) { // Inheritance property
                    String reference = property.get("$ref").getAsString();
                    String path;
                    if (!reference.startsWith("#")) { // Defined in different JSON, so treat as terminal
                        path = reference;
                    } else {
                        path = rootSchema + reference;
                    }
                    if (path.equalsIgnoreCase("ResourceList.schema.json#/definitions/ResourceList")) {
                        continue; // Skip over resource list/do not include as part of schema TODO
                    }
                    return classDefToResourceMap.computeIfAbsent(path, k -> new SchemaResource());
                }
            }
        }
        return null;
    }

    /***
     *
     * @param rootSchema    The file name of the JSON schema definition
     * @param definition    The actual definition itself
     * @param classDefToResourceMap A map containing resources being built/parsed
     * @return A Map of fieldName->properties matching the definition parsed in
     */
    private static Map<String, PropertyDefinition> parseProperties(String rootSchema, JsonObject definition, Map<String, SchemaResource> classDefToResourceMap) {
        Map<String, PropertyDefinition> ret = new HashMap<>();
        if (!definition.has("allOf")) {
            return ret;
        }
        for (JsonElement element : definition.getAsJsonArray("allOf")) {
            JsonObject property = element.getAsJsonObject();
            if (!property.has("$ref")) { // Not inheritance
                JsonObject fields = property.getAsJsonObject("properties");
                for (Map.Entry<String, JsonElement> fieldEntry : fields.entrySet()) {
                    if (fieldEntry.getKey().startsWith("_")) { // Skip extensions
                        continue;
                    }
                    String name = fieldEntry.getKey();
                    JsonObject fieldDef = fields.getAsJsonObject(name);
                    String description = fieldDef.has("description") ? fieldDef.get("description").getAsString() : null;
                    if (fieldDef.has("$ref")) { // Reference Definition
                        String reference = fieldDef.get("$ref").getAsString();
                        String path;
                        if (!reference.startsWith("#")) { // Defined in different JSON, so treat as terminal
                            path = reference;
                        } else {
                            path = rootSchema + reference;
                        }
                        if (path.equalsIgnoreCase("ResourceList.schema.json#/definitions/ResourceList")) {
                            continue; // Skip over resource list/do not include as part of schema TODO
                        }
                        SchemaResource targetResource = classDefToResourceMap.computeIfAbsent(path, k -> new SchemaResource());
                        InstancePropertyDefinition instance = new InstancePropertyDefinition();
                        instance.setTarget(targetResource);
                        instance.setDescription(description);
                        ret.put(name, instance);
                    } else {
                        String type = fieldDef.get("type").getAsString();
                        if (type.equalsIgnoreCase("array")) {
                            ArrayPropertyDefinition arr = new ArrayPropertyDefinition();
                            arr.setDescription(description);
                            JsonObject itemsObj = fieldDef.getAsJsonObject("items");
                            if (itemsObj.has("$ref")) {
                                String reference = itemsObj.get("$ref").getAsString();
                                String path;
                                if (!reference.startsWith("#")) { // Defined in different JSON, so treat as terminal
                                    path = reference;
                                } else {
                                    path = rootSchema + reference;
                                }
                                if (path.equalsIgnoreCase("ResourceList.schema.json#/definitions/ResourceList")) {
                                    continue; // Skip over resource list/do not include as part of schema TODO
                                }
                                SchemaResource targetResource = classDefToResourceMap.computeIfAbsent(path, k -> new SchemaResource());
                                arr.setElementResource(targetResource);
                            } else {
                                arr.setElementValue(itemsObj.get("type").getAsString());
                            }
                            ret.put(name, arr);
                        } else {
                            ValuePropertyDefinition value = new ValuePropertyDefinition(type, description);
                            if (fieldDef.has("pattern")) {
                                Pattern pattern = Pattern.compile(fieldDef.get("pattern").getAsString());
                                value.setValidation(pattern);
                            }
                            if (fieldDef.has("enum")) {
                                JsonArray arr = fieldDef.getAsJsonArray("enum");
                                HashSet<String> enumeration = new HashSet<>();
                                for (JsonElement e : arr) {
                                    enumeration.add(e.getAsString());
                                }
                                value.setEnumeration(enumeration);
                            }
                            ret.put(name, value);
                        }

                    }
                }
            }
        }
        return ret;
    }
}
