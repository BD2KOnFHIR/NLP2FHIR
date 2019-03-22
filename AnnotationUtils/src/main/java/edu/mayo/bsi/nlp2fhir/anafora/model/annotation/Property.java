package edu.mayo.bsi.nlp2fhir.anafora.model.annotation;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.PropertySerializer;

public class Property {
    @JsonIgnore
    private String name;
    @JacksonXmlText
    private Object value;

    public Property() {
    }

    public Property(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
