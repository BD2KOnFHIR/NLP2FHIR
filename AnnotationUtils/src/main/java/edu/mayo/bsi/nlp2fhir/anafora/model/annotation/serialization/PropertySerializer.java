package edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.Property;

import java.io.IOException;
import java.util.List;

public class PropertySerializer extends JsonSerializer<List<Property>> {
    @Override
    public void serialize(List<Property> properties, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
        gen.writeStartObject();
        for (Property p : properties) {
            gen.writeObjectField(p.getName(), p.getValue());
        }
        gen.writeEndObject();
    }
}
