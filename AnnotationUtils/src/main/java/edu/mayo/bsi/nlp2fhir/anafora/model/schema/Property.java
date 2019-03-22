package edu.mayo.bsi.nlp2fhir.anafora.model.schema;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

public class Property {
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "type"
    )
    private String type;
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "input"
    )
    private String input;
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "maxLink"
    )
    private String maxlink;
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "instanceOf"
    )
    private String instanceOf;
    @JacksonXmlText
    private String value;

    public Property(String type, String input, String maxlink, String instanceOf, String value) {
        this.type = type;
        this.input = input;
        this.maxlink = maxlink;
        this.instanceOf = instanceOf;
        this.value = value;
    }

    public Property() {};

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getMaxlink() {
        return maxlink;
    }

    public void setMaxlink(String maxlink) {
        this.maxlink = maxlink;
    }

    public String getInstanceOf() {
        return instanceOf;
    }

    public void setInstanceOf(String instanceOf) {
        this.instanceOf = instanceOf;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
