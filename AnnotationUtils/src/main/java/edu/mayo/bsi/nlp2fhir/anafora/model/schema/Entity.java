package edu.mayo.bsi.nlp2fhir.anafora.model.schema;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

public class Entity {
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "type"
    )
    private String type;

    @JacksonXmlElementWrapper(localName = "properties")
    @JacksonXmlProperty(localName = "property")
    private List<Property> properties;

    public Entity(String type, List<Property> properties) {
        this.type = type;
        this.properties = properties;
    }

    public Entity() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }
}
