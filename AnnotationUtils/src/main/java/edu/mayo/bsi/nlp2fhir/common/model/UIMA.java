package edu.mayo.bsi.nlp2fhir.common.model;

import java.util.HashSet;
import java.util.Set;

public class UIMA {
    public static final Set<String> UIMA_RESERVED_NAMES = new HashSet<>();

    static {
        UIMA_RESERVED_NAMES.add("address");
        UIMA_RESERVED_NAMES.add("cas");
        UIMA_RESERVED_NAMES.add("casimpl");
        UIMA_RESERVED_NAMES.add("class");
        UIMA_RESERVED_NAMES.add("featurevalue");
        UIMA_RESERVED_NAMES.add("featurevalueasstring");
        UIMA_RESERVED_NAMES.add("featurevaluefromstring");
        UIMA_RESERVED_NAMES.add("floatvalue");
        UIMA_RESERVED_NAMES.add("intvalue");
        UIMA_RESERVED_NAMES.add("lowlevelcas");
        UIMA_RESERVED_NAMES.add("stringvalue");
        UIMA_RESERVED_NAMES.add("type");
        UIMA_RESERVED_NAMES.add("view");
        UIMA_RESERVED_NAMES.add("typeindexid");
    }
}
