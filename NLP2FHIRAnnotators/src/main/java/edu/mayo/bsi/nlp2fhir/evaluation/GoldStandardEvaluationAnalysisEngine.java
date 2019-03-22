package edu.mayo.bsi.nlp2fhir.evaluation;

import edu.mayo.bsi.nlp2fhir.evaluation.types.*;
import edu.mayo.bsi.nlp2fhir.performance.structs.AnnotationCache;
import edu.mayo.bsi.nlp2fhir.KnowtatorAnnotation;
import edu.mayo.bsi.nlp2fhir.RegexpStatements;
import edu.mayo.bsi.nlp2fhir.knowtator.KnowtatorPINSCompiler;
import edu.mayo.bsi.nlp2fhir.knowtator.model.KnowtatorAnnotationDef;
import edu.mayo.bsi.nlp2fhir.performance.structs.AnnotationCache;
import edu.mayo.bsi.nlp2fhir.evaluation.types.*;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.hl7.fhir.*;
import org.ohnlp.typesystem.type.textspan.Paragraph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports gold standard annotations from Knowtator instance file (.pins) using the NLP2FHIREvaluation module.<br>
 * May require some modification for external use.
 *
 * TODO needs to be rewritten to use Knowtator API
 */
public class GoldStandardEvaluationAnalysisEngine extends JCasAnnotator_ImplBase {
    private static final Pattern SECTION_START_PATTERN = Pattern.compile("\\[start section id=\"20104\"]");
    private static final Pattern SECTION_END_PATTERN = Pattern.compile("\\[end section id=\"20104\"]");
    public static final String KNOWTATOR_DEF = "ANN_FILE";
    public static final String EVAL_DIR = "EVAL_DIR";
    @ConfigurationParameter(
            name = "ANN_FILE"
    )
    private File knowtatorFile;
    @ConfigurationParameter(
            name = "EVAL_DIR",
            defaultValue = "eval"
    )
    private File evalDir;
    private Map<String, Collection<KnowtatorAnnotationDef>> knowtatorDefsByDocument;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        if (!evalDir.exists()) {
            if (!evalDir.mkdirs()) {
                throw new ResourceInitializationException();
            }
        }
        try {
            knowtatorDefsByDocument = KnowtatorPINSCompiler.importPINSFile(knowtatorFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        // Populate with knowtator defs
        // - Get document ID
        Composition comp = JCasUtil.selectSingle(jCas, Composition.class);
        String docID = comp.getTitle().getValue();
        // - Get collection of annotation definitions for this document
        Collection<KnowtatorAnnotationDef> defs = knowtatorDefsByDocument.getOrDefault(docID, new LinkedList<>());
        // - Populate CAS Structures
        Map<String, Collection<KnowtatorAnnotation>> typeToAnns = new HashMap<>();
        for (KnowtatorAnnotationDef annDef : defs) {
            KnowtatorAnnotation ann = new KnowtatorAnnotation(jCas);
            ann.setBegin(annDef.getStart());
            ann.setEnd(annDef.getEnd());
            ann.setClassdef(annDef.getClassDef());
            ann.setStandardText(annDef.getStandardValue() != null ? annDef.getStandardValue() : annDef.getDocumentText());
            if (ann.getStandardText() == null) {
                ann.setStandardText(ann.getCoveredText());
            }
            ann.addToIndexes();
            if (!typeToAnns.containsKey(annDef.getClassDef())) {
                typeToAnns.put(annDef.getClassDef(), new LinkedList<>());
            }
            typeToAnns.get(annDef.getClassDef()).add(ann);
        }

        // Perform evaluation
        // - Construct allowable ranges (within the 20104 section)
        List<Segment> ranges = new LinkedList<>();
        Matcher m = SECTION_START_PATTERN.matcher(jCas.getDocumentText());
        Matcher m2 = SECTION_END_PATTERN.matcher(jCas.getDocumentText());
        while (m.find()) {
            int searchForEndBegin = m.end();
            if (m2.find(searchForEndBegin)) ranges.add(new Segment(m.start(), m2.end()));
        }

        // - Create a lookup index
        AnnotationCache.AnnotationTree annCache = AnnotationCache.getAnnotationCache(docID, jCas);
        // - Run evaluation MedicationStatement
        // ----- MEDICATIONS ----- TODO don't really need to reinstantiate all this many times
        new MedicationCodeableConceptEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new MedicationFormEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new IngredientNumeratorValueEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new IngredientNumeratorUnitEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new IngredientDenominatorValueEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new IngredientDenominatorUnitEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        // ----- TIMINGS -----
        new TimingDurationValueEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new TimingDurationUnitEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new TimingFrequencyEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new TimingFrequencyMaxEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new TimingPeriodValueEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new TimingPeriodMaxEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new TimingPeriodUnitEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        // ----- DOSAGE -----
        new DosageQuantityEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageQuantityUnitEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageRouteEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageAsNeededBooleanEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageWhenEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageAdditionalInstructionsEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageAsNeededCodeableConceptEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageMethodEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageReasonCodeEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageSiteEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        new DosageTimingCodeEvaluator(evalDir).evaluate(docID, ranges, typeToAnns, annCache);
        // - Declare statistics variables TODO: get rid of the remainder duplicate code
        int truePositiveNLP2FHIR = 0;
        int falsePositiveNLP2FHIR = 0;
        int falseNegativeNLP2FHIR = 0;

        // -- Dosage Quantity range low
        String type = "Dosage.dose.quantity.range.low.value";
        // -- NLP2FHIR
        for (Range r : getInterestedAnnotations(ranges, annCache, Range.class)) {
            if (r.getLow() != null) { // Has a low
                boolean flag = false;
                for (KnowtatorAnnotation ann : annCache.getCollisions(r.getBegin(), r.getEnd(), KnowtatorAnnotation.class)) {
                    if (ann.getClassdef() == null) { // Ummm ???
                        System.out.println("Null classdef in annotation " + ann.getBegin() + ":" + ann.getEnd() + " in " + docID);
                    } else if (ann.getClassdef().equalsIgnoreCase(type)) {
                        if (r.getLow().getValue().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                            flag = true;
                            truePositiveNLP2FHIR++;
                            break;
                        } else {
                            System.out.println("dosagequantityrangelow::" + r.getLow().getValue().getValue() + "::" + ann.getCoveredText());
                        }
                    }
                }
                if (!flag) {
                    falsePositiveNLP2FHIR++;
                    System.out.println("False Positive: " + type + " " + r.getLow().getValue().getValue() + " in " + docID);
                }
            }
        }
        // -- Knowtator (False Negative)
        for (KnowtatorAnnotation ann : typeToAnns.getOrDefault(type, new LinkedList<>())) {
            boolean flag = false;
            for (Range r : annCache.getCollisions(ann.getBegin(), ann.getEnd(), Range.class)) {
                if (r.getLow() != null && r.getLow().getValue() != null && r.getLow().getValue().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                    flag = true;
                }
            }
            if (!flag) {
                falseNegativeNLP2FHIR++;
            }
        }
        // -- Write out results
        File out = new File(evalDir, docID + "-_-_-" + type + ".eval");
        FileWriter writer = null;
        try {
            writer = new FileWriter(out);
                      writer.write(truePositiveNLP2FHIR + "|" + falsePositiveNLP2FHIR + "|" + falseNegativeNLP2FHIR);

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // -- Cleanup
        truePositiveNLP2FHIR = 0;
        falsePositiveNLP2FHIR = 0;
        falseNegativeNLP2FHIR = 0;

        // -- Dosage Quantity range low
        type = "Dosage.dose.quantity.range.low.unit";
        // -- NLP2FHIR
        for (DosageInstruction di : getInterestedAnnotations(ranges, annCache, DosageInstruction.class)) {
            if (di.getDoseRange() != null && di.getDoseRange().getLow() != null && di.getDoseRange().getLow().getUnit() != null) { // Has a low unit
                boolean flag = false;
                for (KnowtatorAnnotation ann : annCache.getCollisions(di.getBegin(), di.getEnd(), KnowtatorAnnotation.class)) {
                    if (ann.getClassdef() == null) { // Ummm ???
                        System.out.println("Null classdef in annotation " + ann.getBegin() + ":" + ann.getEnd() + " in " + docID);
                    } else if (ann.getClassdef().equalsIgnoreCase(type)) {
                        if (di.getDoseRange().getLow().getUnit().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                            flag = true;
                            truePositiveNLP2FHIR++;
                            break;
                        } else {
                            System.out.println("dosagequantityrangelowunit::" + di.getDoseRange().getLow().getUnit().getValue() + "::" + ann.getCoveredText());
                        }
                    }
                }
                if (!flag) {
                    falsePositiveNLP2FHIR++;
                    System.out.println("False Positive: " + type + " " + di.getDoseRange().getLow().getUnit().getValue() + " in " + docID);
                }
            }
        }
        // -- Knowtator (False Negative)
        for (KnowtatorAnnotation ann : typeToAnns.getOrDefault(type, new LinkedList<>())) {
            boolean flag = false;
            for (DosageInstruction di : annCache.getCollisions(ann.getBegin(), ann.getEnd(), DosageInstruction.class)) {
                if (di.getDoseRange().getLow() != null && di.getDoseRange().getLow().getUnit() != null && di.getDoseRange().getLow().getUnit().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                    flag = true;
                }
            }
            if (!flag) {
                falseNegativeNLP2FHIR++;
            }
        }
        // -- Write out results
        out = new File(evalDir, docID + "-_-_-" + type + ".eval");
        writer = null;
        try {
            writer = new FileWriter(out);
                      writer.write(truePositiveNLP2FHIR + "|" + falsePositiveNLP2FHIR + "|" + falseNegativeNLP2FHIR);

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // -- Cleanup
        truePositiveNLP2FHIR = 0;
        falsePositiveNLP2FHIR = 0;
        falseNegativeNLP2FHIR = 0;

        // -- Dosage Quantity range low
        type = "Dosage.dose.quantity.range.high.value";
        // -- NLP2FHIR
        for (Range r : getInterestedAnnotations(ranges, annCache, Range.class)) {
            if (r.getHigh() != null) { // Has a high
                boolean flag = false;
                for (KnowtatorAnnotation ann : annCache.getCollisions(r.getBegin(), r.getEnd(), KnowtatorAnnotation.class)) {
                    if (ann.getClassdef() == null) { // Ummm ???
                        System.out.println("Null classdef in annotation " + ann.getBegin() + ":" + ann.getEnd() + " in " + docID);
                    } else if (ann.getClassdef().equalsIgnoreCase(type)) {
                        if (r.getHigh().getValue().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                            flag = true;
                            truePositiveNLP2FHIR++;
                            break;
                        } else {
                            System.out.println("dosagequantityrangehigh::" + r.getHigh().getValue().getValue() + "::" + ann.getCoveredText());
                        }
                    }
                }
                if (!flag) {
                    falsePositiveNLP2FHIR++;
                    System.out.println("False Positive: " + type + " " + r.getHigh().getValue().getValue() + " in " + docID);
                }
            }
        }
        // -- Knowtator (False Negative)
        for (KnowtatorAnnotation ann : typeToAnns.getOrDefault(type, new LinkedList<>())) {
            boolean flag = false;
            for (Range r : annCache.getCollisions(ann.getBegin(), ann.getEnd(), Range.class)) {
                if (r.getHigh() != null && r.getHigh().getValue() != null && r.getHigh().getValue().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                    flag = true;
                }
            }
            if (!flag) {
                falseNegativeNLP2FHIR++;
            }
        }
        // -- Write out results
        out = new File(evalDir, docID + "-_-_-" + type + ".eval");
        writer = null;
        try {
            writer = new FileWriter(out);
                      writer.write(truePositiveNLP2FHIR + "|" + falsePositiveNLP2FHIR + "|" + falseNegativeNLP2FHIR);

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // -- Cleanup
        truePositiveNLP2FHIR = 0;
        falsePositiveNLP2FHIR = 0;
        falseNegativeNLP2FHIR = 0;

        // -- Dosage Quantity range low
        type = "Dosage.dose.quantity.range.high.unit";
        // -- NLP2FHIR
        for (DosageInstruction di : getInterestedAnnotations(ranges, annCache, DosageInstruction.class)) {
            if (di.getDoseRange() != null && di.getDoseRange().getHigh() != null && di.getDoseRange().getHigh().getUnit() != null) { // Has a low unit
                boolean flag = false;
                for (KnowtatorAnnotation ann : annCache.getCollisions(di.getBegin(), di.getEnd(), KnowtatorAnnotation.class)) {
                    if (ann.getClassdef() == null) { // Ummm ???
                        System.out.println("Null classdef in annotation " + ann.getBegin() + ":" + ann.getEnd() + " in " + docID);
                    } else if (ann.getClassdef().equalsIgnoreCase(type)) {
                        if (di.getDoseRange().getHigh().getUnit().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                            flag = true;
                            truePositiveNLP2FHIR++;
                            break;
                        } else {
                            System.out.println("dosagequantityrangelowunit::" + di.getDoseRange().getLow().getUnit().getValue() + "::" + ann.getCoveredText());
                        }
                    }
                }
                if (!flag) {
                    falsePositiveNLP2FHIR++;
                    System.out.println("False Positive: " + type + " " + di.getDoseRange().getLow().getUnit().getValue() + " in " + docID);
                }
            }
        }
        // -- Knowtator (False Negative)
        for (KnowtatorAnnotation ann : typeToAnns.getOrDefault(type, new LinkedList<>())) {
            boolean flag = false;
            for (DosageInstruction di : annCache.getCollisions(ann.getBegin(), ann.getEnd(), DosageInstruction.class)) {
                if (di.getDoseRange().getHigh() != null && di.getDoseRange().getHigh().getUnit() != null && di.getDoseRange().getHigh().getUnit().getValue().equalsIgnoreCase(ann.getCoveredText())) {
                    flag = true;
                }
            }
            if (!flag) {
                falseNegativeNLP2FHIR++;
            }
        }
        // -- Write out results
        out = new File(evalDir, docID + "-_-_-" + type + ".eval");
        writer = null;
        try {
            writer = new FileWriter(out);
                      writer.write(truePositiveNLP2FHIR + "|" + falsePositiveNLP2FHIR + "|" + falseNegativeNLP2FHIR);

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // -- Cleanup
        truePositiveNLP2FHIR = 0;
        falsePositiveNLP2FHIR = 0;
        falseNegativeNLP2FHIR = 0;
        // -- Dosage as needed
        type = "Dosage.Timing.repeat.dayofWeek";
        // -- NLP2FHIR
        for (TimingRepeat repeati : getInterestedAnnotations(ranges, annCache, TimingRepeat.class)) {
            if (repeati.getDayOfWeek() != null) { // Has days of week
                boolean flag = false;
                for (KnowtatorAnnotation ann : annCache.getCollisions(repeati.getBegin(), repeati.getEnd(), KnowtatorAnnotation.class)) {
                    if (ann.getClassdef() == null) { // Ummm ???
                        System.out.println("Null classdef in annotation " + ann.getBegin() + ":" + ann.getEnd() + " in " + docID);
                    } else if (ann.getClassdef().equalsIgnoreCase(type)) {
                        m = RegexpStatements.WEEKDAY_PARSER.matcher(ann.getCoveredText().toLowerCase());
                        if (m.find()) {
                            for (FeatureStructure fs : repeati.getDayOfWeek().toArray()) {
                                Code c = (Code) fs;
                                if (c.getValue().equalsIgnoreCase(m.group(1).substring(0, 3))) {
                                    flag = true;
                                    truePositiveNLP2FHIR++;
                                }
                            }
                        } else {
                            System.out.println("Weekday not matched::" + ann.getCoveredText().toLowerCase());
                        }
                    }
                }
                if (!flag) {
                    falsePositiveNLP2FHIR++;
                    System.out.println("False Positive: " + type + " " + Arrays.toString(repeati.getDayOfWeek().toStringArray()) + " in " + docID);
                }
            }
        }
        // -- Knowtator (False Negative)
        for (KnowtatorAnnotation ann : typeToAnns.getOrDefault(type, new LinkedList<>())) {
            boolean flag = false;
            for (TimingRepeat repeat : annCache.getCollisions(ann.getBegin(), ann.getEnd(), TimingRepeat.class)) {
                if (repeat.getDayOfWeek() != null) {
                    m = RegexpStatements.WEEKDAY_PARSER.matcher(ann.getCoveredText().toLowerCase());
                    if (m.find()) {
                        for (FeatureStructure fs : repeat.getDayOfWeek().toArray()) {
                            Code c = (Code) fs;
                            if (c.getValue().equalsIgnoreCase(m.group(1).substring(0, 3))) {
                                flag = true;
                                truePositiveNLP2FHIR++;
                            }
                        }
                    } else {
                        System.out.println("Weekday not matched::" + ann.getCoveredText().toLowerCase());
                    }
                }
            }
            if (!flag) {
                falseNegativeNLP2FHIR++;
            }
        }
        // -- Write out results
        out = new File(evalDir, docID + "-_-_-" + type + ".eval");
        writer = null;
        try {
            writer = new FileWriter(out);
                      writer.write(truePositiveNLP2FHIR + "|" + falsePositiveNLP2FHIR + "|" + falseNegativeNLP2FHIR);

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // -- Cleanup
        truePositiveNLP2FHIR = 0;
        falsePositiveNLP2FHIR = 0;
        falseNegativeNLP2FHIR = 0;


        // -- Dosage Quantity range low
        type = "MedicationStatement.effectiveDatetime";
        // -- NLP2FHIR
        for (MedicationStatement ms : getInterestedAnnotations(ranges, annCache, MedicationStatement.class)) {
            if (ms.getEffectiveDateTime() != null) { // Has a date time
                boolean flag = false;
                for (KnowtatorAnnotation ann : annCache.getCollisions(ms.getEffectiveDateTime().getBegin(), ms.getEffectiveDateTime().getEnd(), KnowtatorAnnotation.class)) {
                    if (ann.getClassdef() == null) { // Ummm ???
                        System.out.println("Null classdef in annotation " + ann.getBegin() + ":" + ann.getEnd() + " in " + docID);
                    } else if (ann.getClassdef().equalsIgnoreCase(type)) {
                        flag = true;
                        truePositiveNLP2FHIR++;
                        break;
                    }
                }
                if (!flag) {
                    falsePositiveNLP2FHIR++;
                    System.out.println("False Positive: " + type + " " + ms.getEffectiveDateTime().getValue() + " in " + docID);
                }
            }
        }
        // -- Knowtator (False Negative)
        for (KnowtatorAnnotation ann : typeToAnns.getOrDefault(type, new LinkedList<>())) {
            for (Paragraph p : annCache.getCollisions(ann.getBegin(), ann.getEnd(), Paragraph.class)) {
                for (MedicationStatement ms : annCache.getCollisions(p.getBegin(), p.getEnd(), MedicationStatement.class)) {
                    if (ms.getEffectiveDateTime() == null) {
                        falseNegativeNLP2FHIR++;
                    }
                }
            }
        }
        // -- Write out results
        out = new File(evalDir, docID + "-_-_-" + type + ".eval");
        writer = null;
        try {
            writer = new FileWriter(out);
                      writer.write(truePositiveNLP2FHIR + "|" + falsePositiveNLP2FHIR + "|" + falseNegativeNLP2FHIR);

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private <T extends Annotation> Collection<T> getInterestedAnnotations(List<Segment> segments, AnnotationCache.AnnotationTree annCache, Class<T> clazz) {
        Collection<T> ret = new LinkedHashSet<>(); // Preserve ordering
        for (Segment s : segments) {
            ret.addAll(annCache.getCollisions(s.start, s.end, clazz));
        }
        return ret;
    }

    public static class Segment {
        Segment(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int start;
        public int end;
    }

    @Override
    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        super.collectionProcessComplete(); // TODO
    }
}
