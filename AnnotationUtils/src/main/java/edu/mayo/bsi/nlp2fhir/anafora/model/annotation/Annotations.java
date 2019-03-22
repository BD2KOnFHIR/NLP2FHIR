package edu.mayo.bsi.nlp2fhir.anafora.model.annotation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.AnnotationDeserializer;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.AnnotationSerializer;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.PropertyDeserializer;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.PropertySerializer;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Represents an Anafora annotations XML
 */
@JacksonXmlRootElement(localName = "data")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Annotations {
    @JacksonXmlProperty(localName = "info")
    private AnnotationMeta meta;

    @JacksonXmlProperty(localName = "schema")
    private SchemaInfo schema;

    private AnnotationCollection annotations;

    public AnnotationMeta getMeta() {
        return meta;
    }

    public void setMeta(AnnotationMeta meta) {
        this.meta = meta;
    }

    public SchemaInfo getSchema() {
        return schema;
    }

    public void setSchema(SchemaInfo schema) {
        this.schema = schema;
    }

    public AnnotationCollection getAnnotations() {
        if (annotations == null) {
            annotations = new AnnotationCollection();
        }
        return annotations;
    }

    public void setAnnotations(AnnotationCollection annotations) {
        this.annotations = annotations;
    }
}
