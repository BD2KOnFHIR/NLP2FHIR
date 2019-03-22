package edu.mayo.bsi.nlp2fhir.common.model.schema;

import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Map;

public class FHIRSchema {

    private final Map<String, JsonObject> definitions;
    private final Collection<SchemaResource> resources;
    private final Map<String, SchemaResource> resourceMap;

    public FHIRSchema(Collection<SchemaResource> resources, Map<String, SchemaResource> resourceMap, Map<String, JsonObject> definitions) {
        this.resources = resources;
        this.resourceMap = resourceMap;
        this.definitions = definitions;
    }

    public Collection<SchemaResource> getTopLevelResources() {
        return this.resources;
    }

    public SchemaResource getResourceDefinition(String resourceName) {
        return resourceMap.get(resourceName.toLowerCase());
    }

    public Map<String, JsonObject> getJsonDefinitions() {
        return definitions;
    }

}
