package edu.mayo.bsi.nlp2fhir.anafora.model.schema;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "relations")
public class RelationCollection {
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Relation> relations;

    public RelationCollection(List<Relation> relations) {
        this.relations = relations;
    }

    public RelationCollection() {}

    public List<Relation> getRelations() {
        return relations;
    }

    public void setRelations(List<Relation> relations) {
        this.relations = relations;
    }
}
