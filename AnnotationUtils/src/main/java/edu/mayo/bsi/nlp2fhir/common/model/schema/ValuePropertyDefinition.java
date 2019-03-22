package edu.mayo.bsi.nlp2fhir.common.model.schema;

import java.util.HashSet;
import java.util.regex.Pattern;

public class ValuePropertyDefinition implements PropertyDefinition {
    private String valueType;
    private String description;
    private Pattern validation;
    private HashSet<String> enumeration;

    private ValuePropertyDefinition() {}

    public ValuePropertyDefinition(String valueType, String description) {
        this.valueType = valueType;
        this.description = description;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Pattern getValidation() {
        return validation;
    }

    public void setValidation(Pattern validation) {
        this.validation = validation;
    }

    public HashSet<String> getEnumeration() {
        return enumeration;
    }

    public void setEnumeration(HashSet<String> enumeration) {
        this.enumeration = enumeration;
    }
}
