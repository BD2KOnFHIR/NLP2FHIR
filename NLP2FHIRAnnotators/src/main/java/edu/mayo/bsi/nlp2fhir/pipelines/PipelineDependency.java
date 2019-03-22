package edu.mayo.bsi.nlp2fhir.pipelines;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface PipelineDependency {
    SourceNLPSystem[] required();
    SourceNLPSystem[] recommended() default {};
}
