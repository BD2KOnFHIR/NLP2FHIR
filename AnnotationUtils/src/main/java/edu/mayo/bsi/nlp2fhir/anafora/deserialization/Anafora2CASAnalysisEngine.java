package edu.mayo.bsi.nlp2fhir.anafora.deserialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.AnaforaAnnotation;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.Annotations;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.Property;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.serialization.PropertyDeserializer;
import edu.mayo.bsi.nlp2fhir.common.FHIRJSONSchemaReader;
import edu.mayo.bsi.nlp2fhir.common.model.UIMA;
import edu.mayo.bsi.nlp2fhir.common.model.schema.*;
import edu.mayo.bsi.nlp2fhir.jackson.DuplicateElementUntypedObjectDeserializer;
import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.hl7.fhir.Composition;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads in anafora annotation xmls from a directory, an assumption is made that the collection reader will
 * generate an {@link org.hl7.fhir.Composition} with a title corresponding to an anafora xml with name {title}.xml
 * although individual other fields may be left empty.
 */
public class Anafora2CASAnalysisEngine extends JCasAnnotator_ImplBase {

    private FHIRSchema schema;
    public static final String PARAM_ANNOTATION_DIR = "ANN_DIR";
    @ConfigurationParameter(
            name = "ANN_DIR"
    )
    private File anaforaFolder;

    public static final String PARAM_VIEW_NAME = "VIEW_NAME";
    @ConfigurationParameter(
            name = "VIEW_NAME",
            mandatory = false
    )
    private String viewName;


    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.schema = FHIRJSONSchemaReader.readSchema();
    }

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException {
        String docID = JCasUtil.selectSingle(cas, Composition.class).getTitle().getValue();
        File anaforaDocFolder = new File(new File(anaforaFolder, "FHIR"), docID);
        if (!anaforaDocFolder.exists()) {
            UIMAFramework.getLogger(Anafora2CASAnalysisEngine.class).log(Level.WARNING, "Anafora document folder not found: " + anaforaDocFolder.getPath());
            return;
        }
        File anaforaXML = new File(anaforaDocFolder, docID + ".FHIR.anafora.completed.xml");
        if (!anaforaXML.exists()) {
            UIMAFramework.getLogger(Anafora2CASAnalysisEngine.class).log(Level.WARNING, "Anafora XML not found: " + anaforaXML.getPath());
            return;
        }
        JCas view = cas;
        if (viewName != null) {
            try {
                view = cas.getView(viewName);
                if (view == null) {
                    view = cas.createView(viewName);
                    view.setDocumentText(cas.getDocumentText());
                }
            } catch (CASException e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
        try {
            readAnaforaAnnotationFileIntoView(view, anaforaXML);
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    /**
     * Deserializes a specific anafora annotation xml into a corresponding FHIR CAS format
     * @param view The view to populate
     * @param annotationXML The annotation file
     */
    private void readAnaforaAnnotationFileIntoView(JCas view, File annotationXML) throws IOException {
        Annotations annotations = new XmlMapper()
                .readValue(annotationXML, Annotations.class);
        Map<String, Annotation> readIDs = new HashMap<>();
        Map<String, AnaforaAnnotation> annDefs = new HashMap<>();
        // Populate an ID->Definition map
        for (AnaforaAnnotation ann : annotations.getAnnotations().getAnnotations()) {
            annDefs.put(ann.getId(), ann);
        }
        for (AnaforaAnnotation ann : annotations.getAnnotations().getAnnotations()) {
            if (readIDs.containsKey(ann.getId())) {
                continue;
            }
            try {
                Class<?> unchkdClazz = Class.forName("org.hl7.fhir." + ann.getType());
                if (!Annotation.class.isAssignableFrom(unchkdClazz)) {
                    throw new RuntimeException("Not an annotation: " + unchkdClazz.getName());
                }
                //noinspection unchecked
                readAnaforaAnnotationIntoView(view, ann, (Class<? extends Annotation>) unchkdClazz, annDefs, readIDs); // We don't need to do anything further here as it gets added to readIDs as part of the method
            } catch (Exception e) {
                UIMAFramework.getLogger(Anafora2CASAnalysisEngine.class).log(Level.WARNING,
                        "Could not read annotation " + ann.getId() + " in " + annotationXML.getName(), e);
            }
        }
    }

    /**
     * Reads a single Anafora annotation into the specified view, and recursively generates any linked sub-annotations.
     * These read-in annotations will also be populated within the mapping supplied as a parameter.
     * @param view The view to populate
     * @param entity The anafora annotation to read in
     * @param clazz The CAS class of the corresponding annotation representation
     * @param annDefs A mapping of Anafora annotation IDs to Definitions
     * @param readIDs A mapping of Anafora annotation IDs to CAS annotations that have already been read
     *
     * @return The read-in annotation
     * @throws Exception If an error occurs during annotation processing, particularly during reflection
     */
    private Annotation readAnaforaAnnotationIntoView(JCas view, AnaforaAnnotation entity,
                                                     Class<? extends Annotation> clazz,
                                                     Map<String, AnaforaAnnotation> annDefs,
                                                     Map<String, Annotation> readIDs) throws Exception {
        SchemaResource definition = schema.getResourceDefinition(entity.getType());
        String[] span = entity.getSpan().split(",");
        Annotation ret;
        if (!clazz.getSimpleName().equalsIgnoreCase("Composition")) {
            Constructor<? extends Annotation> ctor = clazz.getConstructor(JCas.class, int.class, int.class);
            ret = ctor.newInstance(view, Integer.valueOf(span[0]), Integer.valueOf(span[1]));
        } else {
            // Don't re-read in Compositions, but rather simply populate values
            ret = JCasUtil.selectSingle(view, Composition.class);
        }
        readIDs.put(entity.getId(), ret);
        for (Property p : entity.getProperties()) {
            String baseName = p.getName();
            if (baseName.equalsIgnoreCase("resourcetype")) {
                continue;
            }
            if (UIMA.UIMA_RESERVED_NAMES.contains(baseName.toLowerCase())) {
                baseName = "fhir" + baseName;
            }
            if (baseName.length() > 1) {
                baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
            } else {
                baseName = baseName.toUpperCase();
            }
            Method getterMethod = clazz.getMethod("get" + baseName);
            Method setterMethod = clazz.getMethod("set" + baseName, getterMethod.getReturnType());
            PropertyDefinition propertyDef = findPropertyDef(p.getName(), definition);
            if (propertyDef instanceof ArrayPropertyDefinition) { // TODO not quite right with the new implementation
                Class<?> arrElementClazz = clazz.getMethod("get" + baseName, int.class).getReturnType();
                if (((ArrayPropertyDefinition) propertyDef).getElementResource() != null) {
                    if (!(p.getValue() instanceof Collection)) {
                        FSArray element = new FSArray(view, 1);
                        @SuppressWarnings("unchecked") Annotation ann = readAnaforaAnnotationIntoView(view,
                                annDefs.get(p.getValue().toString()),
                                (Class<? extends Annotation>) arrElementClazz,
                                annDefs,
                                readIDs);
                        element.set(0, ann);
                        element.addToIndexes();
                        setterMethod.invoke(ret, element);
                    } else {
                        Collection coll = (Collection) p.getValue();
                        FSArray element = new FSArray(view, coll.size());
                        int idx = 0;
                        for (Object o : coll) {
                            @SuppressWarnings("unchecked") Annotation ann = readAnaforaAnnotationIntoView(view,
                                    annDefs.get(o.toString()),
                                    (Class<? extends Annotation>) arrElementClazz,
                                    annDefs,
                                    readIDs);
                            element.set(idx++, ann);
                        }
                        element.addToIndexes();
                        setterMethod.invoke(ret, element);
                    }
                } else {
                    if (!(p.getValue() instanceof Collection)) {
                        FSArray element = new FSArray(view, 1);
                        @SuppressWarnings("unchecked") Constructor<? extends Annotation> propCtor =
                                (Constructor<? extends Annotation>) arrElementClazz.getConstructor(JCas.class, int.class, int.class);
                        Annotation ann = propCtor.newInstance(view, ret.getBegin(), ret.getEnd());
                        Method valueSetter = ann.getClass().getMethod("setValue", String.class);
                        valueSetter.invoke(ann, p.getValue().toString());
                        ann.addToIndexes();
                        element.set(0, ann);
                        element.addToIndexes();
                        setterMethod.invoke(ret, element);
                    } else {
                        Collection coll = (Collection) p.getValue();
                        FSArray element = new FSArray(view, coll.size());
                        int idx = 0;
                        for (Object o : coll) {
                            @SuppressWarnings("unchecked") Constructor<? extends Annotation> propCtor =
                                    (Constructor<? extends Annotation>) arrElementClazz.getConstructor(JCas.class, int.class, int.class);
                            Annotation ann = propCtor.newInstance(view, ret.getBegin(), ret.getEnd());
                            Method valueSetter = ann.getClass().getMethod("setValue", String.class);
                            valueSetter.invoke(ann, o.toString());
                            ann.addToIndexes();
                            element.set(idx++, ann);
                        }
                        element.addToIndexes();
                        setterMethod.invoke(ret, element);
                    }
                }
            } else if (propertyDef instanceof InstancePropertyDefinition) {
                String targetID = p.getValue().toString();
                Annotation target = readIDs.get(targetID);
                if (target == null) {
                    @SuppressWarnings("unchecked") Annotation targetInstance = readAnaforaAnnotationIntoView(view,
                            annDefs.get(targetID),
                            (Class<? extends Annotation>) getterMethod.getReturnType(),
                            annDefs,
                            readIDs);
                    setterMethod.invoke(ret, targetInstance);
                } else {
                    setterMethod.invoke(ret, target);
                }
            } else if (propertyDef instanceof ValuePropertyDefinition) {
                @SuppressWarnings("unchecked") Constructor<? extends Annotation> propCtor =
                        (Constructor<? extends Annotation>) getterMethod.getReturnType().getConstructor(JCas.class, int.class, int.class);
                Annotation ann = propCtor.newInstance(view, ret.getBegin(), ret.getEnd());
                Method valueSetter = ann.getClass().getMethod("setValue", String.class);
                valueSetter.invoke(ann, p.getValue().toString());
                ann.addToIndexes();
            }
        }
        ret.addToIndexes();
        return ret;
    }

    private PropertyDefinition findPropertyDef(String name, SchemaResource definition) {
        SchemaResource curr = definition;
        while (curr != null) {
            PropertyDefinition ret = curr.getDefinitions().get(name);
            if (ret != null) {
                return ret;
            }
            curr = curr.getInherits();
        }
        return null;
    }
}
