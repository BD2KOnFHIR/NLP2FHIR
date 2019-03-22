package edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.AnaforaAnnotation;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.AnnotationCollection;

import java.io.IOException;
import java.util.List;

public class AnnotationSerializer extends StdSerializer<AnnotationCollection> {
    private AnnotationSerializer() {
        this(null);
    }

    private AnnotationSerializer(Class<AnnotationCollection> t) {
        super(t);
    }

    @Override
    public void serialize(AnnotationCollection value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        for (AnaforaAnnotation ann : value.getAnnotations()) {
            gen.writeObjectField(ann.getAnnType().toLowerCase(), ann);
        }
        gen.writeEndObject();
    }
}
