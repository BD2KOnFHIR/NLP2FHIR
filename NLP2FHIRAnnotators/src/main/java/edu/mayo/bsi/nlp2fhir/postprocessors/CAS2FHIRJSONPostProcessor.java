package edu.mayo.bsi.nlp2fhir.postprocessors;

import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducers;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasConsumer_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Resource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class CAS2FHIRJSONPostProcessor extends JCasConsumer_ImplBase {

    @SuppressWarnings("WeakerAccess")
    @ConfigurationParameter(
            name = "OUTPUT_DIR"
    )
    public File outDir;
    public static final String PARAM_OUTPUT_DIR = "OUTPUT_DIR";
    private File compositionsDir;
    private List<Resource> producedResources;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        compositionsDir = new File(outDir, "Composition");
        if (!compositionsDir.exists()) {
            if (!compositionsDir.mkdirs()) {
                throw new IllegalStateException("Could not create composition write directory!");
            }
        }
        producedResources = new LinkedList<>();
    }
    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
        // Start by producing a composition
        Composition composition = ResourceProducers.COMPOSITION.produce(cas).get(0); // Should always be 1 TODO validate this
        producedResources.add(composition);
        File outFile = new File(compositionsDir, composition.getId().split("/")[1] + ".json");
        if (!outFile.getParentFile().exists()) {
            if (!outFile.getParentFile().mkdirs()) {
                throw new IllegalStateException("Could not create parent directory");
            }
        }
        try (FileWriter out = new FileWriter(outFile)) {
            ResourceProducers.FHIRPARSER.encodeResourceToWriter(composition, out);
            out.flush();
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
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
            String id = outRes.getId();
            String[] parsed = id.split("/");
            String type = parsed[0];
            File typeOutDir = new File(outDir, type);
            if (!typeOutDir.exists()) {
                if (!typeOutDir.mkdirs()) {
                    throw new AnalysisEngineProcessException(new RuntimeException("Could not create out dir for " + type));
                }
            }
            try (FileWriter out = new FileWriter(new File(typeOutDir, parsed[1] + ".json"))) {
                ResourceProducers.FHIRPARSER.encodeResourceToWriter(outRes, out);
                out.flush();
            } catch (IOException e) {
                throw new AnalysisEngineProcessException(e);
            }
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
        File typeOutDir = new File(outDir, "ResourceBundle");
        if (!typeOutDir.exists()) {
            if (!typeOutDir.mkdirs()) {
                throw new AnalysisEngineProcessException(new RuntimeException("Could not create out dir for Resource Bundles"));
            }
        }
        try (FileWriter out = new FileWriter(new File(typeOutDir, composition.getId().split("/")[1] + ".json"))) {
            ResourceProducers.FHIRPARSER.encodeResourceToWriter(bundle, out);
            out.flush();
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
        producedResources.clear();
    }

}
