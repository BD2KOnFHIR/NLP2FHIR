package edu.mayo.bsi.nlp2fhir.anafora.model.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name= "schema")
public class Schema {
    private Definition definition;

    public Schema() {this.definition = new Definition();}

    public Definition getDefinition() {
        return definition;
    }

    public void setDefinition(Definition definition) {
        this.definition = definition;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Definition {
        @JacksonXmlElementWrapper(useWrapping = false)
        List<EntityCollection> entities;

        @JacksonXmlElementWrapper(useWrapping = false)
        List<RelationCollection> relations;

        public List<EntityCollection> getEntities() {
            return entities;
        }

        public void setEntities(List<EntityCollection> entities) {
            this.entities = entities;
        }

        public List<RelationCollection> getRelations() {
            return relations;
        }

        public void setRelations(List<RelationCollection> relations) {
            this.relations = relations;
        }
    }
}
