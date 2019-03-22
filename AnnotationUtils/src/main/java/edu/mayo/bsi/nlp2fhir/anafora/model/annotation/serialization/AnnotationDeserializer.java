package edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.AnaforaAnnotation;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.AnnotationCollection;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.Property;
import edu.mayo.bsi.nlp2fhir.jackson.DuplicateElementUntypedObjectDeserializer;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class AnnotationDeserializer extends StdDeserializer<AnnotationCollection> {

    /**
     * See https://github.com/FasterXML/jackson-dataformat-xml/issues/205
     */
    private DuplicateElementUntypedObjectDeserializer jacksonFix;

    public AnnotationDeserializer() {
        this(null);
    }

    private AnnotationDeserializer(Class<?> vc) {
        super(vc);
        this.jacksonFix = new DuplicateElementUntypedObjectDeserializer();
    }

    @Override
    public AnnotationCollection deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        AnnotationCollection wrapper = new AnnotationCollection();
        Object o = jacksonFix.deserialize(p, ctxt);
        if (!(o instanceof HashMap)) {
            throw new IllegalArgumentException("Did not get expected value out of deserializer: expected map, got a " + o.getClass().getName());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> contents = (Map<String, Object>) o;
        List<AnaforaAnnotation> ret = new LinkedList<>();
        contents.forEach((k, v) -> {
            if (v instanceof Collection) {
                //noinspection unchecked
                ((Collection<LinkedHashMap<String, Object>>) v).forEach(m -> {
                    ret.add(extractAnnotationFromMapDefinition(m));
                });
            } else if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) v;
                ret.add(extractAnnotationFromMapDefinition(m));
            } else {
                if (v instanceof String) {
                    if (((String) v).trim().length() != 0) {
                        throw new UnsupportedOperationException("Single or null element annotation " + v);
                    }
                } else {
                    throw new UnsupportedOperationException("Single or null element annotation " + v);
                }
            }
        });
        wrapper.setAnnotations(ret);
        return wrapper;
    }

    private AnaforaAnnotation extractAnnotationFromMapDefinition(Map<String, Object> def) {
        AnaforaAnnotation ann = new AnaforaAnnotation();
        def.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            switch (key.toLowerCase()) {
                case "id":
                    ann.setId(value.toString());
                    break;
                case "span":
                    ann.setSpan(value.toString());
                    break;
                case "type":
                    ann.setType(value.toString());
                    break;
                case "parentstype":
                    ann.setParentsType(value.toString());
                    break;
                case "properties":
                    if (value instanceof Collection) {
                        List<Property> properties = new LinkedList<>();
                        //noinspection unchecked
                        ((Collection<LinkedHashMap<String, Object>>) value)
                                .forEach(propEntry ->
                                        propEntry.forEach((pName, pVal) -> {
                                            if (pVal instanceof Collection) {
                                                //noinspection unchecked
                                                ((Collection) pVal).forEach(val -> properties.add(new Property(pName, val)));
                                            } else {
                                                properties.add(new Property(pName, pVal));
                                            }
                                        }));
                        ann.setProperties(properties);
                    } else if (value instanceof Map) {
                        List<Property> properties = new LinkedList<>();
                        //noinspection unchecked
                        ((Map<String, Object>) value).forEach((pName, pVal) -> {
                            if (pVal instanceof Collection) {
                                //noinspection unchecked
                                ((Collection) pVal).forEach(val -> properties.add(new Property(pName, val)));
                            } else {
                                properties.add(new Property(pName, pVal));
                            }
                        });
                        ann.setProperties(properties);
                    } else {
                        if (value instanceof String) {
                            if (((String) value).trim().length() != 0) {
                                throw new UnsupportedOperationException("Single or null element property " + value.getClass().getName());
                            }
                        } else {
                            throw new UnsupportedOperationException("Single or null element property " + value.getClass().getName());
                        }
                    }
                    break;
                case "addition":
                    break; // Ignore for now TODO
                default:
                    throw new IllegalArgumentException("Undefined property " + key);
            }
        });
        return ann;
    }
}
