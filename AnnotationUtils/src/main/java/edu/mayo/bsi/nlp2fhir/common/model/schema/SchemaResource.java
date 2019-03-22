package edu.mayo.bsi.nlp2fhir.common.model.schema;

import java.util.HashMap;
import java.util.Map;

public class SchemaResource {
    private String name;
    private SchemaResource inherits;
    private String description;
    private Map<String, PropertyDefinition> definitions;

    public SchemaResource() {
        this(null, null, null, new HashMap<>());
    }

    public SchemaResource(String name, SchemaResource inherits, String description, Map<String, PropertyDefinition> definitions) {
        this.name = name;
        this.inherits = inherits;
        this.description = description;
        this.definitions = definitions;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SchemaResource getInherits() {
        return inherits;
    }

    public void setInherits(SchemaResource inherits) {
        this.inherits = inherits;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, PropertyDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, PropertyDefinition> definitions) {
        this.definitions = definitions;
    }
}
