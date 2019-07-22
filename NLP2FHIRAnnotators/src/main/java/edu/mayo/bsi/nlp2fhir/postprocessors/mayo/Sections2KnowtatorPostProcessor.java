//package edu.mayo.bsi.nlp2fhir.postprocessors.mayo;
//
//import edu.mayo.bsi.nlp2fhir.nlp.Section;
//import edu.mayo.bsi.nlp2fhir.nlp.Section;
//import edu.stanford.smi.protege.model.*;
//import edu.uchsc.ccp.knowtator.*;
//import edu.uchsc.ccp.knowtator.textsource.TextSourceAccessException;
//import edu.uchsc.ccp.knowtator.util.ProjectUtil;
//import org.apache.ctakes.typesystem.type.structured.DocumentID;
//import org.apache.uima.UimaContext;
//import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
//import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
//import org.apache.uima.fit.descriptor.ConfigurationParameter;
//import org.apache.uima.fit.util.JCasUtil;
//import org.apache.uima.jcas.JCas;
//import org.apache.uima.resource.ResourceInitializationException;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.Collections;
//import java.util.LinkedList;
//
//public class Sections2KnowtatorPostProcessor extends JCasAnnotator_ImplBase {
//
//
//    private KnowtatorManager knowtator;
//    private AnnotationUtil annotationUtil;
//    private MentionUtil mentionUtil;
//    private Project project;
////    private Cls SECTION_CLS;
////    private Slot SECTION_ID_SLOT;
//
//    @Override
//    public void initialize(UimaContext context) throws ResourceInitializationException {
//        super.initialize(context);
//        File sectionsDir = new File("section_annotations");
//        if (!sectionsDir.exists()) {
//            if (!sectionsDir.mkdirs()) {
//                throw new RuntimeException("Could not create an output directory");
//            }
//        }
//        File pprjFile = new File(sectionsDir, "SECTION_ANNOTATIONS.pprj");
////        project = Project.loadProjectFromURI(pprjFile.toURI(), errors);
//        try {
//            project = ProjectUtil.createNewProject(pprjFile);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        KnowledgeBase kB = project.getKnowledgeBase();
//        kB.deleteCls(kB.getCls("SampleAnnotationType"));
////        SECTION_CLS = kB.createCls("Section", kB.getRootClses());
////        SECTION_ID_SLOT = kB.createSlot("section_id");
////        SECTION_CLS.addDirectTemplateSlot(SECTION_ID_SLOT);
//        KnowtatorProjectUtil kpu = new KnowtatorProjectUtil(kB);
//        knowtator = new KnowtatorManager(kpu);
//        annotationUtil = new AnnotationUtil(knowtator);
//        mentionUtil = new MentionUtil(kpu);
//        annotationUtil.setMentionUtil(mentionUtil);
//        mentionUtil.setAnnotationUtil(annotationUtil);
////        kpu.getConfigurationCls().getInstances().forEach((c) -> c.setDirectOwnSlotValues(kpu.getRootClsesSlot(), Collections.singletonList(SECTION_CLS)));
//        ProjectUtil.saveProject(project);
//    }
//
//    @Override
//    public void process(JCas jCas) throws AnalysisEngineProcessException {
//        for (Section s : JCasUtil.select(jCas, Section.class)) {
//            try {
//                boolean plus1 = s.getCoveredText().startsWith(" ") || s.getCoveredText().startsWith("\n"); // Add one from whitespace at beginning for easier human parse
//                boolean sub1 = s.getCoveredText().endsWith(" ") || s.getCoveredText().endsWith("\n"); // subtract 1 from length to make for easier parsing
//                Cls sectionClass;
//                if ((sectionClass = knowtator.getKnowledgeBase().getCls("Section_" + s.getId())) == null) {
//                    sectionClass = knowtator.getKnowledgeBase().createCls("Section_" + s.getId(), knowtator.getKnowledgeBase().getRootClses());
//                    Collection rootClses = knowtator.getRootClses();
//                    rootClses.removeAll(knowtator.getKnowledgeBase().getRootClses());
//                    rootClses.add(sectionClass);
//                    knowtator.getKnowtatorProjectUtil().getConfigurationCls().getInstances().forEach((c) -> c.setDirectOwnSlotValues(knowtator.getKnowtatorProjectUtil().getRootClsesSlot(), rootClses));
//                }
//                annotationUtil.createAnnotation(
//                        sectionClass,
//                        Collections.singletonList(new Span(s.getBegin() + (plus1 ? 1 : 0), s.getEnd() - (sub1 ? 1 : 0))),
//                        s.getCoveredText(),
//                        JCasUtil.selectSingle(jCas, DocumentID.class).getDocumentID().replace(".xmi", "")
//                        );
////                SimpleInstance slotInstance = mentionUtil.createSlotMention(SECTION_ID_SLOT);
////                mentionUtil.setSlotMentionValues(slotInstance, Collections.singletonList(s.getSectionId()));
////                annotationUtil.setMention(instance, slotInstance);
//            } catch (TextSourceAccessException e) {
//                e.printStackTrace();
//            }
//        }
//        boolean success = false;
//        while (!success) {
//            try {
//                ProjectUtil.saveProject(project);
//                success = true;
//            } catch (Exception ignored) {} // Do it this way due to occasional intermittent IO issues
//        }
//    }
//}
