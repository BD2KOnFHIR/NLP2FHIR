package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl;

import edu.mayo.bsi.nlp2fhir.nlp.Section;
import edu.mayo.bsi.nlp2fhir.nlp.metadata.CompositionResource;
import edu.mayo.bsi.nlp2fhir.nlp.Section;
import edu.mayo.bsi.nlp2fhir.nlp.metadata.CompositionResource;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.Cas2FHIRUtils;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducer;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducers;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

import java.util.*;

public class CompositionResourceProducer implements ResourceProducer<Composition> {
    @Override
    public List<Composition> produce(JCas cas) {
        // Determine run-mode by presence of a composition json resource...
        boolean hasMeta = false;
        Composition composition;
        try {
            CompositionResource res = JCasUtil.selectSingle(cas, CompositionResource.class);
            composition = ResourceProducers.FHIRPARSER.parseResource(Composition.class, res.getSerialized());
            hasMeta = true;
        } catch (IllegalArgumentException ignored) {
            composition = new Composition();
        }
        if (hasMeta) {
            parsePopulatedComposition(cas, composition);
        } else {
            parseNewComposition(cas, composition);
        }
        return Collections.singletonList(composition);
    }

    @Override
    public Composition produce(String documentID, Annotation ann) {
        try {
            return produce(ann.getCAS().getJCas()).get(0);
        } catch (CASException e) {
            throw new RuntimeException(e);
        }
    }

    private void parsePopulatedComposition(JCas cas, Composition composition) {
        // All other fields already present, we just need to parse sections
        // - Map uid->composition sections, and remove our temporary (invalid w/ specifications) extension
        Map<String, Composition.SectionComponent> sectionMap = new HashMap<>();
        for (Composition.SectionComponent section : composition.getSection()) {
            List<Extension> extensions = section.getExtension();
            List<Extension> toRemove = new LinkedList<>(); // Should only be 1
            toRemove.addAll(section.getExtensionsByUrl("temp:auto:nlp2fhir:id"));
            for (Extension e : toRemove) {
                extensions.remove(e);
                sectionMap.put(e.getValue().toString(), section);
            }
        }
        for (Section section : JCasUtil.select(cas, Section.class)) {
            String internalUID = section.getInternalId();
            populateResources(cas, section, sectionMap.get(internalUID));
            sectionMap.get(internalUID).setMode(Composition.SectionMode.WORKING);
        }
        composition.setStatus(Composition.CompositionStatus.FINAL)
                .setConfidentiality(Composition.DocumentConfidentiality.N);
    }

    private void parseNewComposition(JCas cas, Composition composition) {
        // New Composition: set metadata and create sections, and populate resources while doing so
        composition.setId("Composition/" + JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID());
        composition.setStatus(Composition.CompositionStatus.FINAL)
                .setConfidentiality(Composition.DocumentConfidentiality.N);
        composition.setIdentifier(Cas2FHIRUtils.generateUIDIdentifer(UUID.nameUUIDFromBytes(composition.getId().getBytes())));
        for (Section section : JCasUtil.select(cas, Section.class)) {
            Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
            String id = section.getId();
            String name = section.getName();
            sectionComponent.setTitle(name);
            sectionComponent.setCode(new CodeableConcept()
                    .setText(name)
                    .setCoding(
                            Collections.singletonList(new Coding()
                                    .setCode(id)
                                    .setSystem("http://hl7.org/fhir/ValueSet/doc-section-codes")
                                    .setDisplay(name)
                            )
                    ));
            sectionComponent.setText(
                    new Narrative()
                            .setStatus(Narrative.NarrativeStatus.ADDITIONAL)
                            .setDiv(new XhtmlNode(NodeType.Text).setContent(section.getCoveredText()))
            );
            sectionComponent.setMode(Composition.SectionMode.WORKING);
            populateResources(cas, section, sectionComponent);
            composition.getSection().add(sectionComponent);
        }
    }

    // Loads all resources within a given cas and section to the targetted section component
    private void populateResources(JCas cas, Section section, Composition.SectionComponent sectionComponent) {
        String documentID = JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID();
        for (Annotation ann : JCasUtil.selectCovered(cas, Annotation.class, section)) {
            if (ann instanceof org.hl7.fhir.Composition) {
                continue;
            }
            Resource producedResource = ResourceProducers.parseResourceFromCasAnn(documentID, ann);
            if (producedResource != null) {
                sectionComponent.addEntry().setResource(producedResource);
            }
        }
    }
}
