package edu.mayo.bsi.nlp2fhir.anafora.model.annotation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.AnnotationDeserializer;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.AnnotationSerializer;

import java.util.LinkedList;
import java.util.List;

@JsonDeserialize(using = AnnotationDeserializer.class)
@JsonSerialize(using = AnnotationSerializer.class)
public class AnnotationCollection {

    @JsonIgnore
    private List<AnaforaAnnotation> annotations = new LinkedList<>();

    public List<AnaforaAnnotation> getAnnotations() {
        if (annotations == null) {
            annotations = new LinkedList<>();
        }
        return annotations;
    }

    public void setAnnotations(List<AnaforaAnnotation> annotations) {
        this.annotations = annotations;
    }

}
