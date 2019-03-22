package edu.mayo.bsi.nlp2fhir.anafora.model.schema;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

public class Relation {
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "type"
    )
    private String type;

    @JacksonXmlProperty(
            isAttribute = true,
            localName = "color"
    )
    private String color;

    @JacksonXmlElementWrapper(localName = "properties")
    private List<Property> properties;

    public Relation(String type, String color, List<Property> properties) {
        this.type = type;
        this.color = color;
        this.properties = properties;
    }

    public Relation() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }
}

