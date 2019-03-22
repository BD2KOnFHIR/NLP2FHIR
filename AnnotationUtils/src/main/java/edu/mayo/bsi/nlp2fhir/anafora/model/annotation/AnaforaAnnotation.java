package edu.mayo.bsi.nlp2fhir.anafora.model.annotation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.PropertyDeserializer;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.PropertySerializer;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnaforaAnnotation {

    @JacksonXmlProperty(localName = "id")
    private String id;
    @JacksonXmlProperty(localName = "type")
    private String type;
    @JacksonXmlProperty(localName = "parentsType")
    private String parentsType;
    @JacksonXmlElementWrapper(useWrapping = false)
    @JsonSerialize(using = PropertySerializer.class)
    private List<Property> properties;
    @JacksonXmlProperty(localName = "span")
    private String span;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getParentsType() {
        return parentsType;
    }

    public void setParentsType(String parentsType) {
        this.parentsType = parentsType;
    }

    public List<Property> getProperties() {
        if (properties == null) {
            properties = new LinkedList<>();
        }
        return properties;
    }

    public Collection<Object> getProperty(String property) {
        if (properties == null) {
            return null;
        }
        List<Object> ret = new LinkedList<>();
        for (Property prop : properties) {
            if (prop.getName().equalsIgnoreCase(property)) {
                ret.add(prop.getValue());
            }
        }
        return ret;
    }

    public void addProperty(String name, Serializable value) {
        if (properties == null) {
            properties = new LinkedList<>();
        }
        properties.add(new Property(name, value));
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public String getSpan() {
        return span;
    }

    public void setSpan(String span) {
        this.span = span;
    }


    @JsonIgnore
    public String getAnnType() {
        return this.span == null ? ANN_TYPE.RELATION.name() : ANN_TYPE.ENTITY.name();
    }

    public enum ANN_TYPE {
        RELATION,
        ENTITY
    }
}
