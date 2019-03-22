package edu.mayo.bsi.nlp2fhir.common.model.schema;

public class ArrayPropertyDefinition implements PropertyDefinition {
    private String description;
    private SchemaResource elementResource;
    private String elementValue;

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SchemaResource getElementResource() {
        return elementResource;
    }

    public void setElementResource(SchemaResource elements) {
        this.elementResource = elements;
    }

    public String getElementValue() {
        return elementValue;
    }

    public void setElementValue(String elementValue) {
        this.elementValue = elementValue;
    }
}
