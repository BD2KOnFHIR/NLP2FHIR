package edu.mayo.bsi.nlp2fhir.common.model.schema;

public class InstancePropertyDefinition implements PropertyDefinition {
    private SchemaResource target;
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SchemaResource getTarget() {
        return target;
    }

    public void setTarget(SchemaResource target) {
        this.target = target;
    }
}
