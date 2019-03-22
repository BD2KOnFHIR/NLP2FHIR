package edu.mayo.bsi.nlp2fhir.web.controllers;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import edu.mayo.bsi.nlp2fhir.web.pipeline.ProcessingRequest;

import edu.mayo.bsi.uima.server.rest.models.ServerRequest;
import edu.mayo.bsi.uima.server.rest.models.ServerResponse;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Narrative;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Controller
public class PipelineController {
    private IParser FHIRPARSER = FhirContext.forDstu3().newJsonParser().setPrettyPrint(true);


    @GetMapping(value = "/")
    public String resetIndex() {
        return "index";
    }

    @PostMapping("/submit")
    public void produceResourceBundle(HttpServletResponse resp, @RequestBody ProcessingRequest req) throws IOException {
        Composition composition = new Composition();
        for (ProcessingRequest.Section s : req.getSections()) {
            composition.addSection()
                    .setCode(new CodeableConcept().addCoding(new Coding()
                            .setCode(s.id)
                            .setSystem("https://fhir.loinc.org")
                            .setDisplay(s.name))
                    )
                    .setText(new Narrative().setDiv(new XhtmlNode(NodeType.Text).setContent(s.body)));
        }
        composition.setId("Composition/" + UUID.randomUUID());
        try {
            resp.setStatus(200);
            ServerRequest request = new ServerRequest("nlp2fhir", null, FHIRPARSER.encodeResourceToString(composition), Collections.singleton("nlp2fhir"));
            ServerResponse response = new RestTemplate().postForObject("http://localhost:8081/", request, ServerResponse.class);
            resp.getWriter().append(response.getContent().get("nlp2fhir")).flush();
        } catch (Throwable e) {
            resp.setStatus(500);
            e.printStackTrace(resp.getWriter());
        }
    }


}
