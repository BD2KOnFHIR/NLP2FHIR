package edu.mayo.bsi.nlp2fhir.anafora.model.schema;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.LinkedList;
import java.util.List;

@JacksonXmlRootElement(localName = "entities")
public class EntityCollection {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "entity")
    private List<Entity> entities;

    @JacksonXmlProperty(
            isAttribute = true,
            localName = "type"
    )
    private String type;

    public EntityCollection(List<Entity> entities) {
        this.entities = entities;
    }

    public EntityCollection() {
        this.entities = new LinkedList<>();
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void setEntities(List<Entity> entities) {
        this.entities = entities;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
