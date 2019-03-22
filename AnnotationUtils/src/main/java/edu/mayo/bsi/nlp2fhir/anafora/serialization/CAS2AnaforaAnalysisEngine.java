package edu.mayo.bsi.nlp2fhir.anafora.serialization;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import edu.mayo.bsi.nlp2fhir.anafora.AnaforaFHIRSchemaGenerator;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.AnnotationMeta;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.Annotations;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.AnaforaAnnotation;
import edu.mayo.bsi.nlp2fhir.anafora.model.annotation.SchemaInfo;
import edu.mayo.bsi.nlp2fhir.common.FHIRJSONSchemaReader;
import edu.mayo.bsi.nlp2fhir.common.model.UIMA;
import edu.mayo.bsi.nlp2fhir.common.model.schema.*;
import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.hl7.fhir.Composition;
import org.hl7.fhir.Resource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serializes a CAS representation of FHIR resources into an Anafora annotation project
 */
public class CAS2AnaforaAnalysisEngine extends JCasAnnotator_ImplBase {

    private Map<String, AtomicInteger> ANN_ADDRS;
    public static final String PARAM_OUTPUT_DIR = "OUTPUT_DIR";
    @ConfigurationParameter(
            name = "OUTPUT_DIR"
    )
    private File outDir;
    public static final String PARAM_TRANSLATED_RESOURCES = "RESOURCES_USED";
    @ConfigurationParameter(
            name = "RESOURCES_USED",
            mandatory = false
    )
    private List<String> resources;
    public static final String PARAM_ANAFORA_USERNAME = "ANAFORA_USERNAME";
    @ConfigurationParameter(
            name = "ANAFORA_USERNAME"
    )
    private String username;
    public static final String PARAM_ANAFORA_CORPUSNAME = "ANAFORA_CORPUSNAME";
    @ConfigurationParameter(
            name = "ANAFORA_CORPUSNAME"
    )
    private String corpus;
    private FHIRSchema schema;

    private Set<Class<? extends Resource>> resourceClasses;

    private static final DateFormat ANAFORA_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss MM:dd:yyyy");

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.ANN_ADDRS = new HashMap<>();
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) {
                throw new ResourceInitializationException(new IllegalArgumentException("Could not make output directory"));
            }
        }
        this.schema = FHIRJSONSchemaReader.readSchema();
        if (this.resources == null || this.resources.size() == 0) {
            this.resources = new LinkedList<>();
            for (SchemaResource r : this.schema.getTopLevelResources()) {
                if (r.getName().equalsIgnoreCase("Resource") || r.getName().equalsIgnoreCase("DomainResource")) {
                    continue;
                }
                this.resources.add(r.getName());
            }
        }
        try {
            File projSchemaDir = new File(outDir, ".schema");
            if (!projSchemaDir.exists()) {
                if (!projSchemaDir.mkdirs()) {
                    throw new ResourceInitializationException(new IllegalArgumentException("Could not make schema output directory"));
                }
            }
            AnaforaFHIRSchemaGenerator.generateSchema(new File(projSchemaDir, "schema.xml"), resources.toArray(new String[resources.size()]));
        } catch (IOException e) {
            e.printStackTrace();
        }
        File project = new File(outDir, ".setting.xml");
        AnaforaProject proj = new AnaforaProject();
        proj.setProjects(new LinkedList<>());
        proj.setSchemas(new LinkedList<>());
        proj.getProjects().add(new AnaforaProject.Project("NLP2FHIR", "2", "anaforaadmin", "annotator", "FHIR"));
        proj.getSchemas().add(new AnaforaProject.Schema("FHIR", "schema.xml"));
        XmlMapper mapper = new XmlMapper();
        try {
            mapper.writeValue(project, proj);
        } catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
        outDir = new File(outDir, corpus);
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) {
                throw new ResourceInitializationException(new IllegalArgumentException("Could not make output directory"));
            }
        }
        this.resourceClasses = new HashSet<>();
        for (String s : this.resources) {
            try {
                Class<?> clazz = Class.forName("org.hl7.fhir." + s);
                SchemaResource resource = this.schema.getResourceDefinition(s);
                if (resource == null) {
                    throw new IllegalArgumentException();
                }
                if (Resource.class.isAssignableFrom(clazz)) {
                    //noinspection unchecked
                    this.resourceClasses.add((Class<? extends Resource>) clazz);
                }
            } catch (IllegalArgumentException | ClassNotFoundException e) {
                UIMAFramework.getLogger(CAS2AnaforaAnalysisEngine.class).log(Level.WARNING, "No base resource class for " + s + " found, skipping");
            }
        }
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        Map<String, AnaforaAnnotation> entities = new HashMap<>();
        Composition docComposition = JCasUtil.selectSingle(jCas, Composition.class);
        String docID = docComposition.getTitle().getValue();
        initDocument(docID);
        for (Class<? extends Resource> resourceClass : this.resourceClasses) {
            SchemaResource def = this.schema.getResourceDefinition(resourceClass.getSimpleName());
            for (Resource r : JCasUtil.select(jCas, resourceClass)) {
                translateCASResource2Anafora(docID, r, def, entities);
            }
        }
        File outDocFolder = new File(outDir, docID);
        if (!outDocFolder.exists()) {
            if (!outDocFolder.mkdirs()) {
                throw new AnalysisEngineProcessException(new IllegalArgumentException("Cannot make out folder " + outDocFolder.getPath()));
            }
        }
        Annotations docAnnotations = new Annotations();
        List<AnaforaAnnotation> annotations = new ArrayList<>(entities.values());
        docAnnotations.getAnnotations().setAnnotations(annotations);
        AnnotationMeta meta = new AnnotationMeta();
        meta.setProgress("complete");
        meta.setSavetime(ANAFORA_DATE_FORMAT.format(new Date()));
        docAnnotations.setMeta(meta);
        SchemaInfo schemaInfo = new SchemaInfo();
        schemaInfo.setPath("./");
        schemaInfo.setProtocol("file");
        schemaInfo.setValue("schema.xml");
        docAnnotations.setSchema(schemaInfo);
        XmlMapper mapper = new XmlMapper();
        try (FileWriter textWriter = new FileWriter(new File(outDocFolder, docID))) {
            mapper.writeValue(new File(outDocFolder, docID + ".FHIR." + username + ".completed.xml"), docAnnotations);
            textWriter.write(jCas.getDocumentText());
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
        finishDocument(docID);
    }

    private void initDocument(String documentID) {
        ANN_ADDRS.computeIfAbsent(documentID, k -> new AtomicInteger(0));
    }

    //TODO constructor creation of method stack, rather than reinstantiation, document
    private AnaforaAnnotation translateCASResource2Anafora(String documentID, Annotation resource, SchemaResource resourceDef, Map<String, AnaforaAnnotation> existingResources) {
        if (resource == null) {
            return null;
        }
        AnaforaAnnotation ret = existingResources.get(generateUIDforAnnotation(documentID, resource).toString());
        if (ret == null) {
            ret = new AnaforaAnnotation();
            // Anafora annotation ids are 1-indexed... no don't ask me why... but we increment first to preserve this
            int address = ANN_ADDRS.computeIfAbsent(documentID, k -> new AtomicInteger(0)).incrementAndGet();
            String anaforaAnnId = address + "@e@" + documentID + "@NLP2FHIR"; // Address@[e]ntity|[r]elation@Doc@Author
            ret.setId(anaforaAnnId);
            existingResources.put(generateUIDforAnnotation(documentID, resource).toString(), ret);
        } else { // We already previously constructed this annotation
            return ret;
        }
        // Parent Type
        ret.setParentsType("Resources");
        // Type
        ret.setType(resource.getClass().getSimpleName());
        // Span
        ret.setSpan(resource.getBegin() + "," + resource.getEnd());
        // Properties
        for (Map.Entry<String, PropertyDefinition> field : resourceDef.getDefinitions().entrySet()) {
            String methodName = field.getKey();
            if (methodName.equalsIgnoreCase("resourcetype")) {
                continue;
            }
            if (UIMA.UIMA_RESERVED_NAMES.contains(methodName.toLowerCase())) {
                methodName = "fhir" + methodName;
            }
            if (methodName.length() > 1) {
                methodName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            } else {
                methodName = methodName.toUpperCase();
            }
            methodName = "get" + methodName;
            Method retrievalMethod;
            try {
                retrievalMethod = resource.getClass().getMethod(methodName);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
            retrievalMethod.setAccessible(true);
            Object result;
            try {
                result = retrievalMethod.invoke(resource);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
            if (result == null) {
                continue; // No value for property
            }
            PropertyDefinition property = field.getValue();
            if (property instanceof ArrayPropertyDefinition) {
                if (!(result instanceof FSArray)) {
                    throw new RuntimeException("Expected a FS array, got a " + result.getClass().getName());
                }
                SchemaResource elementResource = ((ArrayPropertyDefinition) property).getElementResource();
                String elementType = ((ArrayPropertyDefinition) property).getElementValue();
                if (elementResource != null) {
                    for (FeatureStructure fs : ((FSArray) result).toArray()) {
                        if (!(fs instanceof Annotation)) {
                            throw new IllegalArgumentException("Expected an annotation inside a FS array, instead got a " + fs.getClass().getName());
                        }
                        AnaforaAnnotation targetedEntity = translateCASResource2Anafora(documentID, (Annotation) fs, elementResource, existingResources);
                        if (targetedEntity != null) {
                            ret.addProperty(field.getKey(), targetedEntity.getId());
                        }
                    }
                } else {
                    Method valueMethod = null;
                    for (FeatureStructure fs : ((FSArray) result).toArray()) {
                        if (valueMethod == null) {
                            try {
                                valueMethod = fs.getClass().getMethod("getValue");
                                valueMethod.setAccessible(true);
                            } catch (NoSuchMethodException e) {
                                // TODO monitor this
                                UIMAFramework.getLogger(CAS2AnaforaAnalysisEngine.class).log(Level.WARNING, "Types with no getValue() method are not supported for type copy! Found " + elementType + " -> " + fs.getClass().getName());
                                break;
                            }
                        }
                        try {
                            Object o = valueMethod.invoke(fs);
                            if (o != null) {
                                ret.addProperty(field.getKey(), o.toString());
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            UIMAFramework.getLogger(CAS2AnaforaAnalysisEngine.class).log(Level.WARNING, "Could not successfully copy value in array", e);
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (property instanceof InstancePropertyDefinition) {
                if (!(result instanceof Annotation)) {
                    throw new IllegalStateException("Expected a annotation, got a " + result.getClass().getName());
                }
                SchemaResource target = ((InstancePropertyDefinition) property).getTarget();
                AnaforaAnnotation targetedEntity = translateCASResource2Anafora(documentID, (Annotation) result, target, existingResources);
                if (targetedEntity != null) {
                    ret.addProperty(field.getKey(), targetedEntity.getId());
                }
            }
            if (property instanceof ValuePropertyDefinition) {
                try {
                    Method valueMethod = result.getClass().getMethod("getValue");
                    valueMethod.setAccessible(true);
                    Object o = valueMethod.invoke(result);
                    if (o != null) {
                        ret.addProperty(field.getKey(), o.toString());
                    }
                } catch (NoSuchMethodException e) {
                    UIMAFramework.getLogger(CAS2AnaforaAnalysisEngine.class).log(Level.WARNING, "Types with no getValue() method are not supported for type copy! Tried to copy value for class " + result.getClass().getName());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    UIMAFramework.getLogger(CAS2AnaforaAnalysisEngine.class).log(Level.WARNING, "Could not successfully copy value", e);
                }
            }
        }
        return ret;
    }

    private void finishDocument(String documentID) {
        ANN_ADDRS.remove(documentID);
    }

    private static UUID generateUIDforAnnotation(String docID, Annotation resource) {
        return UUID.nameUUIDFromBytes((docID.toLowerCase() + "::" + resource.getBegin() + ":" + resource.getEnd() + ":" + resource.getCoveredText() + ":" + resource.getClass().getName()).getBytes());
    }

    @JacksonXmlRootElement(localName = "setting")
    private static class AnaforaProject {
        @JacksonXmlElementWrapper(localName = "projects")
        @JacksonXmlProperty(localName = "project")
        List<Project> projects;

        public static class Project {
            @JacksonXmlProperty(isAttribute = true, localName = "name")
            String name;
            @JacksonXmlProperty(isAttribute = true, localName = "numOfAnnotator")
            String numOfAnnotator;
            @JacksonXmlProperty(localName = "admin")
            String admin;
            @JacksonXmlProperty(localName = "annotator")
            String annotator;
            @JacksonXmlProperty(localName = "schema")
            String schema;

            public Project(String name, String numOfAnnotator, String admin, String annotator, String schema) {
                this.name = name;
                this.numOfAnnotator = numOfAnnotator;
                this.admin = admin;
                this.annotator = annotator;
                this.schema = schema;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getNumOfAnnotator() {
                return numOfAnnotator;
            }

            public void setNumOfAnnotator(String numOfAnnotator) {
                this.numOfAnnotator = numOfAnnotator;
            }

            public String getAdmin() {
                return admin;
            }

            public void setAdmin(String admin) {
                this.admin = admin;
            }

            public String getAnnotator() {
                return annotator;
            }

            public void setAnnotator(String annotator) {
                this.annotator = annotator;
            }

            public String getSchema() {
                return schema;
            }

            public void setSchema(String schema) {
                this.schema = schema;
            }
        }

        @JacksonXmlElementWrapper(localName = "schemas")
        @JacksonXmlProperty(localName = "schema")
        List<Schema> schemas;

        public static class Schema {
            @JacksonXmlProperty(isAttribute = true, localName = "name")
            String name;
            @JacksonXmlProperty(localName = "file")
            String file;

            public Schema(String name, String file) {
                this.name = name;
                this.file = file;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getFile() {
                return file;
            }

            public void setFile(String file) {
                this.file = file;
            }
        }

        public List<Project> getProjects() {
            return projects;
        }

        public void setProjects(List<Project> projects) {
            this.projects = projects;
        }

        public List<Schema> getSchemas() {
            return schemas;
        }

        public void setSchemas(List<Schema> schemas) {
            this.schemas = schemas;
        }
    }

}
