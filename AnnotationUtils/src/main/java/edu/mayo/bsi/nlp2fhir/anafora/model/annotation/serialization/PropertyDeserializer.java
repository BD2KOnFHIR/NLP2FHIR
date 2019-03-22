package edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.Property;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class PropertyDeserializer extends StdDeserializer<List<Property>> {

    public PropertyDeserializer() {
        this(null);
    }

    private PropertyDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public List<Property> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        List<Property> ret = new LinkedList<>();
        if (node.getNodeType().equals(JsonNodeType.ARRAY)) {
            for (JsonNode item : node) {
                if (item.getNodeType().equals(JsonNodeType.OBJECT)) {
                    Map<String, Object> map = new ObjectMapper().convertValue(item, new TypeReference<Map<String, Object>>() {
                    });
                    map.forEach((key, value) -> ret.add(new Property(key, value)));
                }
            }
        } else if (node.getNodeType().equals(JsonNodeType.OBJECT)) {
            Map<String, Object> map = new ObjectMapper().convertValue(node, new TypeReference<Map<String, Object>>() {
            });
            map.forEach((key, value) -> ret.add(new Property(key, value)));
        } else {
            Logger.getLogger("Property Deserialization").warning("Invalid input " + node.toString() + " as an input property");
        }
        return ret;
    }
}
