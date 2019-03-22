package edu.mayo.bsi.nlp2fhir.anafora.model.annotation;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

public class SchemaInfo {
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "path"
    )
    private String path;
    @JacksonXmlProperty(
            isAttribute = true,
            localName = "protocol"
    )
    private String protocol;
    @JacksonXmlText
    private String value;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
