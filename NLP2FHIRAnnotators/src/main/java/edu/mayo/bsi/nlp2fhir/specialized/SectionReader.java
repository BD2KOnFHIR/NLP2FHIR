//package edu.mayo.bsi.nlp2fhir.specialized;
//
//import edu.mayo.bsi.nlp2fhir.nlp.Section;
//import edu.stanford.smi.protege.model.Cls;
//import edu.stanford.smi.protege.model.KnowledgeBase;
//import edu.stanford.smi.protege.model.Project;
//import edu.stanford.smi.protege.model.SimpleInstance;
////import edu.uchsc.ccp.knowtator.AnnotationUtil;
////import edu.uchsc.ccp.knowtator.KnowtatorManager;
////import edu.uchsc.ccp.knowtator.KnowtatorProjectUtil;
////import edu.uchsc.ccp.knowtator.MentionUtil;
////import edu.uchsc.ccp.knowtator.util.ProjectUtil;
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
//import java.util.Collection;
//import java.util.List;
//
///**
// * Reads in section definitions from a knowtator definition
// */
//public class SectionReader extends JCasAnnotator_ImplBase {
//
//    public static final String PARAM_PROJECT_FILE = "PROJECT_FILE";
//    @SuppressWarnings("WeakerAccess")
//    @ConfigurationParameter(
//            name = "PROJECT_FILE",
//            description = "The project file containing FHIR element definitions"
//    )
//    private File pprjFile;
//    private KnowtatorManager knowtator;
//    private AnnotationUtil annotationUtil;
//
//    @Override
//    public void initialize(UimaContext context) throws ResourceInitializationException {
//        super.initialize(context);
//        Project project = ProjectUtil.openProject(pprjFile.getAbsolutePath());
//        KnowledgeBase kB = project.getKnowledgeBase();
//        knowtator = new KnowtatorManager(new KnowtatorProjectUtil(kB));
//        annotationUtil = new AnnotationUtil(knowtator);
//        MentionUtil mentionUtil = new MentionUtil(knowtator.getKnowtatorProjectUtil());
//        mentionUtil.setAnnotationUtil(annotationUtil);
//        annotationUtil.setMentionUtil(mentionUtil);
//    }
//
//    @Override
//    public void process(JCas cas) throws AnalysisEngineProcessException {
//        String docID = JCasUtil.selectSingle(cas, DocumentID.class).getDocumentID();
//        List<SimpleInstance> sections = annotationUtil.getAnnotations(docID);
//        if (sections == null) {
//            return;
//        }
//        for (SimpleInstance sectionAnnotation : sections) {
//            Collection spanColl = sectionAnnotation.getOwnSlotValues(knowtator.getKnowtatorProjectUtil().getAnnotationSpanSlot());
//            for (Object o : spanColl) {
//                // Parse out spans
//                String[] split = o.toString().split("\\|");
//                Cls value = (Cls) annotationUtil
//                        .getMention(sectionAnnotation)
//                        .getOwnSlotValue(knowtator.getKnowtatorProjectUtil().getMentionClassSlot());
//                if (value == null) {
//                    continue;
//                }
//                String sectionID = value.getName();
//                // TODO generalize/map to a shared section representation used by FHIR
//                Section s = new Section(cas, Integer.valueOf(split[0]), Integer.valueOf(split[1]));
//                s.setId(sectionID.split("_")[0]);
//                s.addToIndexes();
//            }
//        }
//    }
//}
