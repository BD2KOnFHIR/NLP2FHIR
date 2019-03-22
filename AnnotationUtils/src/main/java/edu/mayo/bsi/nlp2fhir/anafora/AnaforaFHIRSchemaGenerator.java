package edu.mayo.bsi.nlp2fhir.anafora;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import edu.mayo.bsi.nlp2fhir.anafora.model.schema.Entity;
import edu.mayo.bsi.nlp2fhir.anafora.model.schema.EntityCollection;
import edu.mayo.bsi.nlp2fhir.anafora.model.schema.Property;
import edu.mayo.bsi.nlp2fhir.anafora.model.schema.Schema;
import edu.mayo.bsi.nlp2fhir.common.FHIRJSONSchemaReader;
import edu.mayo.bsi.nlp2fhir.common.model.schema.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Generates an Anafora Schema XML from FHIR resources. Because Anafora doesn't have an API(yet), this is based on the
 * example schema mirrored at https://raw.githubusercontent.com/weitechen/anafora/242e09a3ac0f35c81ed0da1df54de89b39ab4756/sample%20data/sampleanaforaprojectfile/.schema/demomedicalschema.xml
 */
public class AnaforaFHIRSchemaGenerator {

    /**
     * Generates an Anafora Schema XML Representation based on an input FHIR JSON Schema
     *
     * @param schemaXmlOut The output file for the generated schema
     * @param resources    (Optional) varargs of resource names to include in schema, will default to all
     */
    public static void generateSchema(File schemaXmlOut, String... resources) throws IOException {
        FHIRSchema schema = FHIRJSONSchemaReader.readSchema();
        Set<SchemaResource> resourcesToCollect = new HashSet<>();
        if (resources.length > 0) {
            for (String resName : resources) {
                SchemaResource resource = schema.getResourceDefinition(resName);
                if (resource != null) {
                    resourcesToCollect.add(resource);
                }
            }
        } else {
            resourcesToCollect.addAll(schema.getTopLevelResources());
        }
        EntityCollection resourceColl = new EntityCollection(); // TODO: consider switching to a per-resource properties/entity coll
        resourceColl.setType("Resources");
        // Recursively ensure that all referenced resources are /also/ available
        for (SchemaResource resource : new HashSet<>(resourcesToCollect)) {
            recursEnsureAvailability(resource, resourcesToCollect);
        }
        for (SchemaResource resource : resourcesToCollect) {
            Entity resourceEntity = new Entity();
            resourceEntity.setType(resource.getName());
            resourceEntity.setProperties(new ArrayList<>(getProperties(resource).values()));
            resourceColl.getEntities().add(resourceEntity);
        }
        resourceColl.getEntities().sort(Comparator.comparing(Entity::getType));
        Schema outSchema = new Schema();
        outSchema.getDefinition().setEntities(Collections.singletonList(resourceColl));
        XmlMapper mapper = new XmlMapper();
        mapper.writeValue(schemaXmlOut, outSchema);
    }

    private static void recursEnsureAvailability(SchemaResource resource, Set<SchemaResource> resourcesToCollect) {
        for (PropertyDefinition def : resource.getDefinitions().values()) {
            if (def instanceof InstancePropertyDefinition) {
                SchemaResource target = ((InstancePropertyDefinition) def).getTarget();
                if (!resourcesToCollect.contains(target)) {
                    resourcesToCollect.add(target);
                    recursEnsureAvailability(target, resourcesToCollect);
                }
            } else if (def instanceof ArrayPropertyDefinition) {
                if (((ArrayPropertyDefinition) def).getElementResource() != null) {
                    SchemaResource target = ((ArrayPropertyDefinition) def).getElementResource();
                    if (!resourcesToCollect.contains(target)) {
                        resourcesToCollect.add(target);
                        recursEnsureAvailability(target, resourcesToCollect);
                    }
                }
            }
        }
    }

    private static LinkedHashMap<String, Property> getProperties(SchemaResource resource) {
        LinkedHashMap<String, Property> properties = new LinkedHashMap<>();
        if (resource.getInherits() != null) {
            properties.putAll(getProperties(resource.getInherits()));
        }
        for (Map.Entry<String, PropertyDefinition> propertyDef : resource.getDefinitions().entrySet()) {
            Property property = new Property();
            property.setType(propertyDef.getKey());
            PropertyDefinition definition = propertyDef.getValue();
            if (definition instanceof InstancePropertyDefinition) {
                property.setInput("list");
                property.setInstanceOf(((InstancePropertyDefinition) definition).getTarget().getName());
                property.setMaxlink("1");
            } else if (definition instanceof ArrayPropertyDefinition) {
                if (((ArrayPropertyDefinition) definition).getElementResource() != null) {
                    property.setInput("list");
                    property.setInstanceOf(((ArrayPropertyDefinition) definition).getElementResource().getName());
                    property.setMaxlink(null);
                } else if (((ArrayPropertyDefinition) definition).getElementValue() != null) {
                    property.setInput("text"); // No way (as of yet) to make a multi-text selection in Anafora
                }
            } else if (definition instanceof ValuePropertyDefinition) {
                if (((ValuePropertyDefinition) definition).getEnumeration() != null && ((ValuePropertyDefinition) definition).getEnumeration().size() > 0) {
                    property.setInput("choice");
                    property.setValue("," + String.join(",", ((ValuePropertyDefinition) definition).getEnumeration()));
                } else {
                    property.setInput("text");
                }
            }
            properties.put(propertyDef.getKey(), property);
        }
        return properties;
    }

    public static void main(String... args) throws IOException {
        generateSchema(new File("anafora_fhir_schema.xml"), args);
    }
}
