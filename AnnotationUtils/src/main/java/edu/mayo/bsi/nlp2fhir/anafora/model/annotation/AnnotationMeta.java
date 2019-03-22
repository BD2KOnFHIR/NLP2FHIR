package edu.mayo.bsi.nlp2fhir.anafora.model.annotation;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

public class AnnotationMeta {
    @JacksonXmlProperty(localName = "savetime")
    private String savetime;
    @JacksonXmlProperty(localName = "progress")
    private String progress;

    public String getSavetime() {
        return savetime;
    }

    public void setSavetime(String savetime) {
        this.savetime = savetime;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }
}
