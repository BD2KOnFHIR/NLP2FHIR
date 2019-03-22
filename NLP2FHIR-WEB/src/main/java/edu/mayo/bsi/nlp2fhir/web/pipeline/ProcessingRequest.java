package edu.mayo.bsi.nlp2fhir.web.pipeline;

public class ProcessingRequest {
    private Section[] sections;

    public Section[] getSections() {
        return sections;
    }

    public void setSections(Section[] sections) {
        this.sections = sections;
    }

    public static class Section {
        public String id;
        public String name;
        public String body;
    }
}
