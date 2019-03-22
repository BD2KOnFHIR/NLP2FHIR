package edu.mayo.bsi.nlp2fhir.stream;

import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducers;
import edu.mayo.bsi.uima.server.api.UIMANLPResultSerializer;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Resource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NLP2FHIRResourceBundleSerializer implements UIMANLPResultSerializer {
    @Override
    public Serializable serializeNLPResult(CAS casimpl) {
        try {
            List<Resource> producedResources = new ArrayList<>();
            JCas cas = casimpl.getJCas().getView("text");
            // Start by producing a composition
            Composition composition = ResourceProducers.COMPOSITION.produce(cas).get(0); // Should always be 1 TODO validate this
            producedResources.add(composition);
            // Produce resources
            for (org.hl7.fhir.Resource resource : JCasUtil.select(cas, org.hl7.fhir.Resource.class)) {
                if (resource instanceof org.hl7.fhir.Composition) {
                    continue;
                }
                Resource outRes = ResourceProducers.parseResourceFromCasAnn(composition.getId().split("/")[1], resource);
                if (outRes == null) { // Unsupported resource output
                    continue;
                }
                producedResources.add(outRes);
            }
            Bundle bundle = new Bundle();
            bundle.setId("Bundle/" + UUID.randomUUID().toString());
            producedResources.sort((o1, o2) -> {
                // Always have Composition first
                if (o1 instanceof Composition) {
                    if (o2 instanceof Composition) {
                        return o1.getId().compareTo(o2.getId());
                    } else {
                        return -1;
                    }
                } else if (o2 instanceof Composition) {
                    // Implies o1 is not a composition
                    return 1;
                } else {
                    return o1.getId().compareTo(o2.getId());
                }
            });
            for (Resource resource : producedResources) {
                Bundle.BundleEntryComponent entry = bundle.addEntry();
                entry.setResource(resource);
                entry.setFullUrl(resource.getId());
            }
            bundle.setType(Bundle.BundleType.DOCUMENT);
            return ResourceProducers.FHIRPARSER.encodeResourceToString(bundle);
        } catch (CASException e) {
            e.printStackTrace();
            return e;
        }
    }
}
