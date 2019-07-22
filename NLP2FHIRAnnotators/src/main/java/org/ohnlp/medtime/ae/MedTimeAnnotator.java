/*******************************************************************************
 * Copyright: (c)  2013  Mayo Foundation for Medical Education and 
 *  Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 *  triple-shield Mayo logo are trademarks and service marks of MFMER.
 *
 *  Except as contained in the copyright notice above, or as used to identify
 *  MFMER as the author of this software, the trade names, trademarks, service
 *  marks, or product names of the copyright holder shall not be used in
 *  advertising, promotion or otherwise in connection with this software without
 *  prior written authorization of the copyright holder.
 *
 *  MedTime is free software: you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  MedTime is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with MedTime.  If not, see http://www.gnu.org/licenses/.
 *
 *******************************************************************************/
package org.ohnlp.medtime.ae;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import org.ohnlp.medtime.resourcemanager.GenericResourceManager;
import org.ohnlp.medtime.resourcemanager.NormalizationManager;
import org.ohnlp.medtime.resourcemanager.RePatternManager;
import org.ohnlp.medtime.resourcemanager.RuleManager;
import org.ohnlp.medtime.util.DateCalculator;
import org.ohnlp.medtime.util.ContextAnalyzer;
import org.ohnlp.medtime.util.Logger;
import org.ohnlp.medtime.util.Toolbox;
import org.ohnlp.medtime.type.DocumentMetadata;
import org.ohnlp.typesystem.type.textspan.Sentence;
import org.ohnlp.medtime.type.MedTimex3;
import org.ohnlp.typesystem.type.syntax.BaseToken;

/**
 * MedTime finds temporal expressions and normalizes them according to the
 * TIMEX3 TimeML annotation standard. Adapted from HeidelTime.
 *
 * @author Hongfang Liu & Sunghwan Sohn
 * <p>
 * Patched version with bugfix
 */
public class MedTimeAnnotator extends JCasAnnotator_ImplBase {

    // TOOL NAME (may be used as componentId)
    private Class<?> component = this.getClass();


    // COUNTER FOR TIMEX IDS
    private int timexID = 0;

    // INPUT PARAMETER HANDLING WITH UIMA
    private String PARAM_REPORTFORMAT = "reportFormat";
    private String PARAM_RESOURCE_DIR = "Resource_dir";
    private String reportFormat = "i2b2";
    private String resource_dir = ""; //TODO check it!!

    // INPUT PARAMETER HANDLING WITH UIMA (which types shall be extracted)
    private String PARAM_DATE = "Date";
    private String PARAM_TIME = "Time";
    private String PARAM_DURATION = "Duration";
    private String PARAM_SET = "Set";
    private Boolean find_dates = true;
    private Boolean find_times = true;
    private Boolean find_durations = true;
    private Boolean find_sets = true;
    private int cYear = 0;
    private int cMonth = 0;
    private int cCentury = 0;
    private MedTimex3 opdate;
    private MedTimex3 admdate;
    private MedTimex3 disdate;

    // lowercase will transform the sentence to lower case for pattern matching
    private Boolean lowerCase = true;

    @SuppressWarnings("unused")
    public void initialize(UimaContext aContext) throws ResourceInitializationException {

        super.initialize(aContext);

        // ///////////////////////////////
        // DEBUGGING PARAMETER SETTING //
        // ///////////////////////////////

        Logger.setPrintDetails(false);

        // ////////////////////////////////
        // GET CONFIGURATION PARAMETERS //
        // ////////////////////////////////
        reportFormat = (String) aContext.getConfigParameterValue(PARAM_REPORTFORMAT);
        resource_dir = (String) aContext.getConfigParameterValue(PARAM_RESOURCE_DIR);
        find_dates = (Boolean) aContext.getConfigParameterValue(PARAM_DATE);
        find_times = (Boolean) aContext.getConfigParameterValue(PARAM_TIME);
        find_durations = (Boolean) aContext.getConfigParameterValue(PARAM_DURATION);
        find_sets = (Boolean) aContext.getConfigParameterValue(PARAM_SET);

        // ////////////////////////////////////////
        // SET REPORT FORMAT FOR RESOURCE PROCESSING //
        // ////////////////////////////////////////
        GenericResourceManager.REPORTFORMAT = reportFormat;
        GenericResourceManager.RESOURCEDIR = resource_dir;
        // //////////////////////////////////////////////////////////
        // READ NORMALIZATION RESOURCES FROM FILES AND STORE THEM //
        // //////////////////////////////////////////////////////////
        NormalizationManager norm = NormalizationManager.getInstance();

        // ////////////////////////////////////////////////////
        // READ PATTERN RESOURCES FROM FILES AND STORE THEM //
        // ////////////////////////////////////////////////////
        RePatternManager repm = RePatternManager.getInstance();

        // /////////////////////////////////////////////////
        // READ RULE RESOURCES FROM FILES AND STORE THEM //
        // /////////////////////////////////////////////////
        RuleManager rulem = RuleManager.getInstance();

        // ///////////////////////////
        // PRINT WHAT WILL BE DONE //
        // ///////////////////////////
        if (find_dates)
            Logger.printDetail("Getting Dates...");
        if (find_times)
            Logger.printDetail("Getting Times...");
        if (find_durations)
            Logger.printDetail("Getting Durations...");
        if (find_sets)
            Logger.printDetail("Getting Sets...");

    }

    /**
     * @see JCasAnnotator_ImplBase#process(JCas)
     */
    public void process(JCas jcas) {
        try {
            RuleManager rulem = RuleManager.getInstance();
            // //////////////////////////////////////////
            // CHECK SENTENCE BY SENTENCE FOR TIMEXES //
            // //////////////////////////////////////////
            FSIterator<? extends Annotation> sentIter = jcas.getAnnotationIndex(Sentence.type).iterator();
            while (sentIter.hasNext()) {
                Sentence s = (Sentence) sentIter.next();
                if (find_dates) {
                    findTimexes("DATE", rulem.getHmDatePattern(), rulem.getHmDateOffset(),
                            rulem.getHmDateNormalization(), rulem.getHmDateQuant(), s, jcas);
                }
                if (find_times) {
                    findTimexes("TIME", rulem.getHmTimePattern(), rulem.getHmTimeOffset(),
                            rulem.getHmTimeNormalization(), rulem.getHmTimeQuant(), s, jcas);
                }
                if (find_durations) {
                    findTimexes("DURATION", rulem.getHmDurationPattern(), rulem.getHmDurationOffset(),
                            rulem.getHmDurationNormalization(), rulem.getHmDurationQuant(), s, jcas);
                }
                if (find_sets) {
                    findTimexes("SET", rulem.getHmSetPattern(), rulem.getHmSetOffset(),
                            rulem.getHmSetNormalization(), rulem.getHmSetQuant(), s, jcas);
                }
            }


            // ///////////////////////////////////////////////////////////////////////////////
            // SUBPROCESSOR CONFIGURATION. REGISTER YOUR OWN PROCESSORS HERE FOR EXECUTION //
            // ///////////////////////////////////////////////////////////////////////////////
            ProcessorManager pm = ProcessorManager.getInstance();
            pm.registerProcessor("org.ohnlp.medtime.ae.HolidayProcessor");
            pm.registerProcessor("org.ohnlp.medtime.ae.RemoveOverlapTimexProcessor");
            pm.registerProcessor("org.ohnlp.medtime.ae.RemoveInvalidTimexProcessor");
            pm.executeAllProcessors(jcas);

            updateTypes(jcas);
            findAnchorDates(jcas);
            specifyAmbiguousValues(jcas);
            processOPValues(jcas);

            pm.registerProcessor("org.ohnlp.medtime.ae.RemoveDateIfTimeProcessor");
            pm.executeAllProcessors(jcas);
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    /**
     * Add timex annotation to CAS object.
     *
     * @param timexType
     * @param begin
     * @param end
     * @param timexValue
     * @param timexId
     * @param foundByRule
     * @param jcas
     */
    public void addTimexAnnotation(String timexType, int begin, int end,
                                   Sentence sentence, String timexValue, String timexQuant,
                                   String timexFreq, String timexMod, String timexId,
                                   String foundByRule, JCas jcas) {

        MedTimex3 annotation = new MedTimex3(jcas);
        annotation.setBegin(begin);
        annotation.setEnd(end);
        annotation.setContextSentence(sentence);

        FSIterator<? extends Annotation> iterToken = jcas.getAnnotationIndex(BaseToken.type).subiterator(sentence);
        String allTokIds = "";
        while (iterToken.hasNext()) {
            BaseToken tok = (BaseToken) iterToken.next();
            if (tok.getBegin() == begin) {

                annotation.setFirstTokId(tok.getTokenNumber());
                allTokIds = "BEGIN<-->" + tok.getTokenNumber();
            }
            if ((tok.getBegin() > begin) && (tok.getEnd() <= end)) {
                allTokIds = allTokIds + "<-->" + tok.getTokenNumber();
            }
        }
        if (timexType.equals("DATE") &&
                (timexValue.indexOf("hour") >= 0 || timexValue.indexOf("minute") >= 0 || timexValue.indexOf("second") >= 0)) {
            timexValue = "UNDEF-this-day";
        }
        annotation.setAllTokIds(allTokIds);

        annotation.setTimexType(timexType);
        annotation.setTimexValue(timexValue);
        annotation.setTimexId(timexId);
        annotation.setFoundByRule(foundByRule);
        if ((timexType.equals("DATE")) || (timexType.equals("TIME"))) {
            if ((timexValue.startsWith("X"))
                    || (timexValue.startsWith("UNDEF"))) {
                annotation.setFoundByRule(foundByRule + "-relative");
            } else {
                annotation.setFoundByRule(foundByRule + "-explicit");
            }
        }

        if (!(timexQuant == null)) {
            annotation.setTimexQuant(timexQuant);
        }
        if (!(timexFreq == null)) {
            annotation.setTimexFreq(timexFreq);
        }
        if (!(timexMod == null)) {
            annotation.setTimexMod(timexMod);
        }
        if ((timexType.equals("DURATION"))) {
            if (timexValue.indexOf("X") > 0) {
                annotation.setTimexValue(timexValue.replace('X', '3'));
                annotation.setTimexMod("APPROX");
            }
        }
        annotation.addToIndexes();
        if (annotation.getContextSentence().getSegment().getValue().indexOf("date") >= 0) {
            for (Object mr : Toolbox.findMatches(Pattern.compile("([0-9][0-9])-([0-9]?[0-9])-"), annotation.getTimexValue())) {
                cYear = Integer.parseInt((String) ((MatchResult) mr).group(1));
                cMonth = Integer.parseInt((String) ((MatchResult) mr).group(2));
            }
        }

        Logger.printDetail(annotation.getTimexId() + "EXTRACTION PHASE:   "
                + " found by:" + annotation.getFoundByRule() + " text:"
                + annotation.getCoveredText());
        Logger.printDetail(annotation.getTimexId() + "NORMALIZATION PHASE:"
                + " found by:" + annotation.getFoundByRule() + " text:"
                + annotation.getCoveredText() + " value:"
                + annotation.getTimexValue());

    }

    /**
     * Postprocessing: Remove invalid timex expressions. These are already
     * marked as invalid: timexValue().equals("REMOVE")
     *
     * @param jcas
     */
    //This is specifically for operation day rules
    // POD 3
    public void processOPValues(JCas jcas) {
        List<MedTimex3> linearDates = new ArrayList<MedTimex3>();
        FSIterator<? extends Annotation> iterTimex = jcas.getAnnotationIndex(MedTimex3.type).iterator();
        //	System.out.println(opdate);

        // Create List of all Timexes of types "date" and "time"
        while (iterTimex.hasNext()) {
            MedTimex3 timex = (MedTimex3) iterTimex.next();
            if (timex.getTimexType().equals("DATE")) {
                linearDates.add(timex);
            }
        }
        for (int i = 0; i < linearDates.size(); i++) {
            MedTimex3 t_i = (MedTimex3) linearDates.get(i);
            if (t_i.getContextSentence().getSegment().getValue().indexOf("hospital_course") >= 0
                    && !opdate.getTimexValue().matches("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")) {
                String lmDay = ContextAnalyzer.getLastMentionedX(linearDates, i, "day");
                if (lmDay.matches("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]"))
                    opdate.setTimexValue(lmDay);
                else
                    opdate = admdate;
                break;
            }
        }
        for (int i = 0; i < linearDates.size(); i++) {
            MedTimex3 t_i = (MedTimex3) linearDates.get(i);
            if (t_i.getTimexValue().startsWith("OPDATE")) {
                String[] parts = t_i.getTimexValue().split("\\-");
                int addDay = 0;
                if (parts[1].equals("PLUS")) {
                    addDay = Integer.parseInt(parts[2]);
                }
                t_i.removeFromIndexes();
                t_i.setTimexValue(DateCalculator.getXNextDay(opdate.getTimexValue(), addDay));
                t_i.addToIndexes();
            }
        }
    }

    public void findAnchorDates(JCas jcas) {
        admdate = null;
        disdate = null;
        opdate = null;
        List<MedTimex3> linearDates = new ArrayList<MedTimex3>();
        FSIterator<? extends Annotation> iterTimex = jcas.getAnnotationIndex(MedTimex3.type).iterator();

        // Create List of all Timexes of types "date" and "time"
        while (iterTimex.hasNext()) {
            MedTimex3 timex = (MedTimex3) iterTimex.next();
            if (timex.getTimexType().equals("DATE")
                    && !timex.getTimexValue().equals("REMOVE")) {
                linearDates.add(timex);
            }
        }

        boolean foundAdmDate = false;
        boolean foundDisDate = false;
        for (MedTimex3 t : linearDates) {
//need to do..
            if (t.getContextSentence().getSegment().getValue().startsWith("admission_date") && t.getCoveredText().toLowerCase().indexOf("adm") < 0) {
                admdate = t;
                foundAdmDate = true;
            } else if (t.getContextSentence().getSegment().getValue().startsWith("discharge_date") && t.getCoveredText().toLowerCase().indexOf("dis") < 0) {
                disdate = t;
                foundDisDate = true;
            }

            if (foundAdmDate && foundDisDate) {
                break;
            }
        }
        // adhoc to avoid error in i2b2
        if (admdate == null && linearDates.size() > 0) {
            admdate = linearDates.get(0);
            //System.out.println("No admission date");
        }
        if (disdate == null && admdate != null) {
            System.out.println("No discharge date. Will assign admission date " +
                    "to discharge date to avoid algorithm error");
            disdate = admdate;
        }
        opdate = null;
        for (MedTimex3 t : linearDates) {
            if (t.getTimexValue().startsWith("OPDATE"))
                break;
            String myText = t.getContextSentence().getCoveredText();
            if (t.getContextSentence().getSegment().getValue().startsWith("hospital_course")) {
                if (opdate == null && (myText.indexOf("underwent") >= 0 || myText.indexOf("operat") >= 0)) {
                    opdate = t;
                    break;
                }
            }
        }
        if (opdate == null)
            opdate = admdate;
    }

    // -----

    public boolean updateTypes(JCas jcas) {
        boolean changed = false;
        List<MedTimex3> linearDates = new ArrayList<MedTimex3>();
        RuleManager rulem = RuleManager.getInstance();
        FSIterator<? extends Annotation> iterTimex = jcas.getAnnotationIndex(MedTimex3.type).iterator();

        // Create List of all Timexes of types "date" and "time"
        while (iterTimex.hasNext()) {
            MedTimex3 timex = (MedTimex3) iterTimex.next();
            if (timex.getTimexType().equals("DATE")) {
                linearDates.add(timex);
            }
            if (cCentury == 0)
                for (Object mr : Toolbox.findMatches(Pattern
                        .compile("(19|20)([0-9])([0-9])-([0-9]?[0-9])-"), timex
                        .getTimexValue())) {
                    cCentury = Integer.parseInt(((MatchResult) mr).group(1));
                }
        }
        for (int i = 0; i < linearDates.size(); i++) {
            MedTimex3 t_i = (MedTimex3) linearDates.get(i);
            Sentence sentence = t_i.getContextSentence();
            String contextS = sentence.getCoveredText();
            if (t_i.getBegin() > sentence.getBegin()) {
                String prefix = contextS.substring(0, t_i.getBegin()
                        - sentence.getBegin() - 1);
                // System.out.println(prefix);
                boolean updated = false;
                if (prefix.endsWith("for")) {
                    if (find_durations) {
                        updated = findTimexes("DURATION", rulem.getHmDurationPattern(),
                                rulem.getHmDurationOffset(), rulem.getHmDurationNormalization(),
                                rulem.getHmDurationQuant(), sentence, jcas);
                    }
                    if (updated)
                        t_i.removeFromIndexes();
                }
            }
        }
        return changed;
    }


    /**
     * Under-specified values are disambiguated here. Only types
     * "date" and "time" can be under-specified.
     *
     * @param jcas
     */
    @SuppressWarnings({"unused"})
    public boolean specifyAmbiguousValues(JCas jcas) {
        boolean changed = false;
        NormalizationManager norm = NormalizationManager.getInstance();

        // build up a list with all found TIMEX expressions
        List<MedTimex3> linearDates = new ArrayList<MedTimex3>();
        // Add by Hongfang for duration
        List<MedTimex3> durationDates = new ArrayList<MedTimex3>();
        List<MedTimex3> linearSets = new ArrayList<MedTimex3>();
        List<MedTimex3> opSets = new ArrayList<MedTimex3>();

        FSIterator<? extends Annotation> iterTimex = jcas.getAnnotationIndex(MedTimex3.type).iterator();

        // Create List of all Timexes of types "date" and "time"
        while (iterTimex.hasNext()) {
            MedTimex3 timex = (MedTimex3) iterTimex.next();
            if (timex.getTimexType().equals("DATE")
                    || timex.getTimexType().equals("TIME")) {
                linearDates.add(timex);
            }
            // Add by Hongfang for duration
            if (timex.getTimexType().equals("DURATION")) {
                durationDates.add(timex);
            }

            if (timex.getTimexType().equals("SET")) {
                linearSets.add(timex);
            }
            if (timex.getFoundByRule().indexOf("date_r27") > 0)
                opSets.add(timex);
        }

        for (int i = 0; i < durationDates.size(); i++) {
            MedTimex3 t_i = (MedTimex3) durationDates.get(i);
            String value_i = t_i.getTimexValue();
            String valueNew = value_i;
            int pos = value_i.indexOf("K");
            if (pos >= 0) {
                String type = "P";
                String unit = value_i.substring(value_i.length() - 1);
                String v1 = value_i.substring(1, pos);
                String v2 = value_i.substring(pos + 1, value_i.length() - 1);
                if (v1.startsWith("T")) {
                    type = "PT";
                    v1 = v1.substring(1);
                }
                int pos1 = v1.indexOf("F");
                if (pos1 >= 0) {
                    String v11 = v1.substring(0, pos1);
                    String v12 = v1.substring(pos1 + 2);
                    double v = Double.parseDouble(v2) + Double.parseDouble(v11)
                            / Double.parseDouble(v12);
                    valueNew = type + v + unit;
                } else {
                    double x = (Double.parseDouble(v2) + Double.parseDouble(v1)) / 2.0;
                    valueNew = type + x + unit;
                }
            }
            t_i.removeFromIndexes();
            Logger.printDetail(t_i.getTimexId()
                    + " DISAMBIGUATION PHASE: foundBy:" + t_i.getFoundByRule()
                    + " text:" + t_i.getCoveredText() + " value:"
                    + t_i.getTimexValue() + " NEW value:" + valueNew);
            // if(!
            // t_i.getContextSentence().getCoveredText().substring(0,t_i.getBegin()-t_i.getContextSentence().getBegin()-1).endsWith("who is")){
            t_i.setTimexValue(valueNew);
            t_i.addToIndexes();
            durationDates.set(i, t_i);
            // }
        }

        // ---Frequency normalization
        DecimalFormat formatter = new DecimalFormat("0.00");
        for (MedTimex3 tx : linearSets) {
            String txVal = tx.getTimexValue();
            String txFreq = tx.getTimexFreq();

            if (txVal.startsWith("NORMFVAL")) {
                // eg) txVal=NORMFVAL-DATE
                String[] parts = txVal.split("\\-");
                String num = "";
                String valNew = "";
                double normVal;

                if (parts[1].equals("DATE")) {
                    normVal = 1.0 / Float.parseFloat(txFreq);
                    num = Toolbox.removeTrailingZeros(formatter.format(normVal));
                    valNew = "RP" + num + txVal.substring(txVal.length() - 1);
                } else if (parts[1].equals("TIME")) {
                    normVal = 1.0 / Float.parseFloat(txFreq);
                    num = Toolbox.removeTrailingZeros(formatter.format(normVal));
                    valNew = "RPT" + num + txVal.substring(txVal.length() - 1);
                } else if (parts[1].equals("HOURSUM")) {
                    normVal = Float.parseFloat(parts[2])
                            / Float.parseFloat(txFreq);
                    num = Toolbox.removeTrailingZeros(formatter.format(normVal));
                    valNew = "RPT" + num + "H";
                }
                // eg) times 3 doses (val=RPT8H)
                else if (parts[1].equals("DOSE")) {
                    normVal = 24.0 / Float.parseFloat(txFreq);
                    num = Toolbox.removeTrailingZeros(formatter.format(normVal));
                    valNew = "RPT" + num + "H";
                }

                tx.removeFromIndexes();
                tx.setTimexValue(valNew);
                tx.addToIndexes();
            }
        }
        // //////////////////////////////////////
        // IS THERE A DOCUMENT CREATION TIME? //
        // //////////////////////////////////////
        boolean dctAvailable = false;


        // get the dct information
        String dctValue = "";
        int dctCentury = 0;
        int dctYear = 0;
        int dctDecade = 0;
        int dctMonth = 0;
        int dctDay = 0;
        String dctSeason = "";
        String dctQuarter = "";
        String dctHalf = "";
        int dctWeekday = 0;
        int dctWeek = 0;

        // ////////////////////////////////////////////
        // INFORMATION ABOUT DOCUMENT CREATION TIME //
        // ////////////////////////////////////////////
        FSIterator<? extends Annotation> dctIter = jcas.getAnnotationIndex(DocumentMetadata.type).iterator();
        if (dctIter.hasNext()) {
            dctAvailable = true;
            DocumentMetadata dct = (DocumentMetadata) dctIter.next();
            dctValue = dct.getTimexValue();
            // year, month, day as mentioned in the DCT
            if (dctValue.matches("\\d\\d\\d\\d\\d\\d\\d\\d")) {
                dctCentury = Integer.parseInt(dctValue.substring(0, 2));
                dctYear = Integer.parseInt(dctValue.substring(0, 4));
                dctDecade = Integer.parseInt(dctValue.substring(2, 3));
                dctMonth = Integer.parseInt(dctValue.substring(4, 6));
                dctDay = Integer.parseInt(dctValue.substring(6, 8));

                Logger.printDetail("dctCentury:" + dctCentury);
                Logger.printDetail("dctYear:" + dctYear);
                Logger.printDetail("dctDecade:" + dctDecade);
                Logger.printDetail("dctMonth:" + dctMonth);
                Logger.printDetail("dctDay:" + dctDay);
            } else {
                dctCentury = Integer.parseInt(dctValue.substring(0, 2));
                dctYear = Integer.parseInt(dctValue.substring(0, 4));
                dctDecade = Integer.parseInt(dctValue.substring(2, 3));
                dctMonth = Integer.parseInt(dctValue.substring(5, 7));
                dctDay = Integer.parseInt(dctValue.substring(8, 10));

                Logger.printDetail("dctCentury:" + dctCentury);
                Logger.printDetail("dctYear:" + dctYear);
                Logger.printDetail("dctDecade:" + dctDecade);
                Logger.printDetail("dctMonth:" + dctMonth);
                Logger.printDetail("dctDay:" + dctDay);
            }
            dctQuarter = "Q"
                    + norm.getFromNormMonthInQuarter(norm
                    .getFromNormNumber(dctMonth + ""));
            dctHalf = "H1";
            if (dctMonth > 6) {
                dctHalf = "H2";
            }

            // season, week, weekday, have to be calculated
            dctSeason = norm.getFromNormMonthInSeason(norm
                    .getFromNormNumber(dctMonth + "")
                    + "");
            dctWeekday = DateCalculator.getWeekdayOfDate(dctYear + "-"
                    + norm.getFromNormNumber(dctMonth + "") + "-"
                    + norm.getFromNormNumber(dctDay + ""));
            dctWeek = DateCalculator.getWeekOfDate(dctYear + "-"
                    + norm.getFromNormNumber(dctMonth + "") + "-"
                    + norm.getFromNormNumber(dctDay + ""));

            Logger.printDetail("dctQuarter:" + dctQuarter);
            Logger.printDetail("dctSeason:" + dctSeason);
            Logger.printDetail("dctWeekday:" + dctWeekday);
            Logger.printDetail("dctWeek:" + dctWeek);
        } else {
            Logger.printDetail("No DCT available...");
        }

        // ////////////////////////////////////////////
        // go through list of Date and Time timexes //
        // ////////////////////////////////////////////
        if (opSets.size() > 0 && linearDates.size() > 0) {
            MedTimex3 timex = (MedTimex3) linearDates.get(linearDates.size() - 1);
            if (timex.getContextSentence().getCoveredText().indexOf("discharge") >= 0) {
                String[] parts = timex.getTimexValue().split("\\-");
                int diff = Integer.parseInt(parts[parts.length - 1]) * (-1);
                String valueNew = DateCalculator.getXNextDay(disdate.getTimexValue(), diff);
                opdate.setTimexValue(valueNew);
            }
        }

        for (int i = 0; i < linearDates.size(); i++) {
            MedTimex3 t_i = (MedTimex3) linearDates.get(i);
            String value_i = t_i.getTimexValue();

            // check if value_i has month, day, season, week (otherwise no
            // UNDEF-year is possible)
            Boolean viHasMonth = false;
            Boolean viHasDay = false;
            Boolean viHasSeason = false;
            Boolean viHasWeek = false;
            Boolean viHasQuarter = false;
            Boolean viHasHalf = false;
            int viThisMonth = 0;
            int viThisDay = 0;
            String viThisSeason = "";
            String viThisQuarter = "";
            String viThisHalf = "";
            String[] valueParts = value_i.split("-");

            // check if UNDEF-year or UNDEF-century
            if ((value_i.startsWith("UNDEF-year"))
                    || (value_i.startsWith("UNDEF-century"))) {
                if (valueParts.length > 2) {
                    // get vi month
                    if (valueParts[2].matches("\\d\\d")) {
                        viHasMonth = true;
                        viThisMonth = Integer.parseInt(valueParts[2]);
                    }
                    // get vi season
                    else if ((valueParts[2].equals("SP"))
                            || (valueParts[2].equals("SU"))
                            || (valueParts[2].equals("FA"))
                            || (valueParts[2].equals("WI"))) {
                        viHasSeason = true;
                        viThisSeason = valueParts[2];
                    }
                    // get v1 quarter
                    else if ((valueParts[2].equals("Q1"))
                            || (valueParts[2].equals("Q2"))
                            || (valueParts[2].equals("Q3"))
                            || (valueParts[2].equals("Q4"))) {
                        viHasQuarter = true;
                        viThisQuarter = valueParts[2];
                    } else if ((valueParts[2].equals("H1"))
                            || (valueParts[2].equals("H2"))) {
                        viHasHalf = true;
                        viThisHalf = valueParts[2];
                    }
                    // get vi day
                    if ((valueParts.length > 3)
                            && (valueParts[3].matches("\\d\\d"))) {
                        viHasDay = true;
                        viThisDay = Integer.parseInt(valueParts[3]);
                    }
                }
            } else {
                if (valueParts.length > 1) {
                    // get vi month
                    if (valueParts[1].matches("\\d\\d")) {
                        viHasMonth = true;
                        viThisMonth = Integer.parseInt(valueParts[1]);
                    }
                    // get vi season
                    else if ((valueParts[1].equals("SP"))
                            || (valueParts[1].equals("SU"))
                            || (valueParts[1].equals("FA"))
                            || (valueParts[1].equals("WI"))) {
                        viHasSeason = true;
                        viThisSeason = valueParts[1];
                    }
                    // get vi day
                    if ((valueParts.length > 2)
                            && (valueParts[2].matches("\\d\\d"))) {
                        viHasDay = true;
                        viThisDay = Integer.parseInt(valueParts[2]);
                    }
                }
            }
            // get the last tense (depending on the part of speech tags used in
            // front or behind the expression)
            String last_used_tense = ContextAnalyzer.getLastTense(t_i, jcas);

            // ////////////////////////
            // DISAMBIGUATION PHASE //
            // ////////////////////////
            String valueNew = value_i;

            // ---added by Sunghwan
            // TODO check it!
            if (t_i.getTimexType().equals("DATE")) {
                // eg: ADMDATE-PLUS-0
                if (valueNew.startsWith("ADMDATE")
                        || valueNew.startsWith("DISDATE")) {
                    String[] parts = valueNew.split("-");
                    int addDay = 0;

                    if (parts[1].equals("PLUS"))
                        addDay = Integer.parseInt(parts[2]);
                    else if (parts[1].equals("MINUS"))
                        addDay = -Integer.parseInt(parts[2]);
                    else {
                        System.err.println("Incorrect PLUS/MINUS i ADMDATE in:"
                                + valueNew);
                        System.exit(1);
                    }

                    if (valueNew.startsWith("ADMDATE")) {
                        valueNew = DateCalculator.getXNextDay(admdate.getTimexValue(), addDay);
                    } else if (valueNew.startsWith("DISDATE")) {
                        //   System.out.println("disdate:"+value_i+"|"+disdate.getTimexValue());
                        valueNew = DateCalculator.getXNextDay(disdate.getTimexValue(), addDay);
                    }
                }
                // eg: HOSPITALDAY-digit
                else if (valueNew.startsWith("HOSPITALDAY")) {
                    String[] parts = valueNew.split("\\-");
                    int addDay = Integer.parseInt(parts[1]) - 1;
                    valueNew = DateCalculator.getXNextDay(admdate.getTimexValue(), addDay);
                } else if (valueNew.startsWith("OPDATE")
                        && opdate.getTimexValue().matches("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")) {
                    String[] parts = valueNew.split("\\-");
                    int addDay = 0;
                    if (parts[1].equals("PLUS"))
                        addDay = Integer.parseInt(parts[2]);
                    valueNew = DateCalculator.getXNextDay(opdate.getTimexValue(), addDay);
                } else if (valueNew.startsWith("INFER")) {
                    String[] parts = valueNew.split("-");
                    int addDay = 0;
                    if (parts[2].equals("PLUS"))
                        addDay = Integer.parseInt(parts[3]);
                    else if (parts[2].equals("MINUS"))
                        addDay = -Integer.parseInt(parts[3]);
                    MedTimex3 previousDate = null;
                    for (int inx = i - 1; inx > 0; inx--) {
                        if (linearDates.get(inx).getTimexType().equals("DATE")) {
                            previousDate = linearDates.get(inx);
                            break;
                        }
                    }
                    if (previousDate != null) {
                        if (!previousDate.getContextSentence().getSegment().getId().equals(t_i.getContextSentence().getSegment().getId())
                                || t_i.getContextSentence().getCoveredText().indexOf("prior to admission") >= 0
                                || t_i.getContextSentence().getSegment().getValue().startsWith("history_present_illness")
                                || parts[1].equals("adm")) {
                            valueNew = DateCalculator.getXNextDay(admdate.getTimexValue(), addDay);
                        } else if (parts[1].equals("dis")
                                || t_i.getContextSentence().getCoveredText().indexOf("prior to discharge") >= 0) {
                            valueNew = DateCalculator.getXNextDay(disdate.getTimexValue(), addDay);

                        } else {
                            if (previousDate.getTimexValue().contains("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")) {
                                valueNew = DateCalculator.getXNextDay(previousDate.getTimexValue(), addDay);
                            } else {
                                valueNew = valueNew.replace("INFER-day", "UNDEF-this-day");
                                //DateCalculator.getXNextDay(previousDate
                                //	.getTimexValue(), addDay);
                                // else valueNew=previousDate.getTimexValue();
                            }

                        }
                    } else valueNew = valueNew.replace("INFER-day", "UNDEF-this-day");
                }
            } else {

                if (valueNew.startsWith("ADMDATE")
                        || valueNew.startsWith("DISDATE")) {
                    String[] parts = valueNew.split("\\-");
                    int addDay = 0;
                    int pos = parts[2].indexOf("T");
                    String time = parts[2].substring(pos);
                    parts[2] = parts[2].substring(0, pos);
                    if (parts[1].equals("PLUS"))
                        addDay = Integer.parseInt(parts[2]);
                    else if (parts[1].equals("MINUS"))
                        addDay = -Integer.parseInt(parts[2]);
                    else {
                        System.err.println("Incorrect PLUS/MINUS i ADMDATE in:"
                                + value_i);
                        System.exit(1);
                    }

                    if (valueNew.startsWith("ADMDATE")) {
                        valueNew = DateCalculator.getXNextDay(admdate
                                .getTimexValue(), addDay)
                                + time;
                    } else if (valueNew.startsWith("DISDATE")) {
                        valueNew = DateCalculator.getXNextDay(disdate
                                .getTimexValue(), addDay)
                                + time;
                    }
                }
            }
            // ----

            // //////////////////////////////////////////////////
            // IF YEAR IS COMPLETELY UNSPECIFIED (UNDEF-year) //
            // //////////////////////////////////////////////////
            if (valueNew.startsWith("UNDEF-year")) {
                String newYearValue = dctYear + "";
                // vi has month (ignore day)
                if (viHasMonth == true && (viHasSeason == false)) {
                    // WITH DOCUMENT CREATION TIME
                    if (dctAvailable) {
                        // Tense is FUTURE
                        if ((last_used_tense.equals("FUTURE"))
                                || (last_used_tense.equals("PRESENTFUTURE"))) {
                            // if dct-month is larger than vi-month, than add 1
                            // to dct-year
                            if (dctMonth > viThisMonth) {
                                int intNewYear = dctYear + 1;
                                newYearValue = intNewYear + "";
                            }
                        }
                        // Tense is PAST
                        if ((last_used_tense.equals("PAST"))) {
                            // if dct-month is smaller than vi month, than
                            // substrate 1 from dct-year
                            if (dctMonth < viThisMonth) {
                                int intNewYear = dctYear - 1;
                                newYearValue = intNewYear + "";
                            }
                        }
                    }
                    // WITHOUT DOCUMENT CREATION TIME
                    else {
                        // System.out.println("WITHOUT DOCUMENT CREATION TIME");

                        newYearValue = ContextAnalyzer.getLastMentionedX(
                                linearDates, i, "year");
                        if (cYear > 50)
                            cCentury = 19;
                        else
                            cCentury = 20;
                        String cYearValue = cCentury + "" + cYear + "";
                        if (cYear < 10)
                            cYearValue = cCentury + "0" + cYear + "";
                        // PATCH: Normalize
                        if (newYearValue.length() == 0) newYearValue = "0";
                        if (cYearValue.length() == 0) cYearValue = "0";
                        if (newYearValue.length() == 1) {
                            newYearValue = cYearValue;
                        } else if (Math.abs(Integer.parseInt(newYearValue)
                                - Integer.parseInt(cYearValue)) > 2) {
                            int lyear = Integer.parseInt(cYearValue);
                            if (viThisMonth > cMonth + 2)
                                lyear = lyear - 1;
                            newYearValue = lyear + "";
                        }
                    }
                }
                // vi has quaurter
                if (viHasQuarter == true) {
                    // WITH DOCUMENT CREATION TIME
                    if (dctAvailable) {
                        // Tense is FUTURE
                        if ((last_used_tense.equals("FUTURE"))
                                || (last_used_tense.equals("PRESENTFUTURE"))) {
                            if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))) {
                                int intNewYear = dctYear + 1;
                                newYearValue = intNewYear + "";
                            }
                        }
                        // Tense is PAST
                        if ((last_used_tense.equals("PAST"))) {
                            if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))) {
                                int intNewYear = dctYear - 1;
                                newYearValue = intNewYear + "";
                            }
                        }
                        // IF NO TENSE IS FOUND
                        if (last_used_tense.equals("")) {
                            if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))) {
                                int intNewYear = dctYear + 1;
                                newYearValue = intNewYear + "";
                            } else {
                                // IN NEWS: past temporal expressions
                                if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))) {
                                    int intNewYear = dctYear - 1;
                                    newYearValue = intNewYear + "";
                                }
                            }
                        }
                    }
                    // WITHOUT DOCUMENT CREATION TIME
                    else {
                        newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year");
                    }
                }

                // vi has half
                if (viHasHalf == true) {
                    // WITH DOCUMENT CREATION TIME
                    if (dctAvailable) {
                        // Tense is FUTURE
                        if ((last_used_tense.equals("FUTURE"))
                                || (last_used_tense.equals("PRESENTFUTURE"))) {
                            if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))) {
                                int intNewYear = dctYear + 1;
                                newYearValue = intNewYear + "";
                            }
                        }
                        // Tense is PAST
                        if ((last_used_tense.equals("PAST"))) {
                            if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))) {
                                int intNewYear = dctYear - 1;
                                newYearValue = intNewYear + "";
                            }
                        }
                        // IF NO TENSE IS FOUND
                        if (last_used_tense.equals("")) {
                            if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))) {
                                int intNewYear = dctYear + 1;
                                newYearValue = intNewYear + "";
                            } else {
                                if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))) {
                                    int intNewYear = dctYear - 1;
                                    newYearValue = intNewYear + "";
                                }
                            }
                        }
                    }
                    // WITHOUT DOCUMENT CREATION TIME
                    else {
                        newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year");
                    }
                }

                // vi has season
                if ((viHasMonth == false) && (viHasDay == false)
                        && (viHasSeason == true)) {
                    // TODO check tenses?
                    // WITH DOCUMENT CREATION TIME
                    if (dctAvailable) {
                        newYearValue = dctYear + "";
                    }
                    // WITHOUT DOCUMENT CREATION TIME
                    else {
                        newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year");
                    }
                }
                // vi has week
                if (viHasWeek) {
                    // WITH DOCUMENT CREATION TIME
                    if (dctAvailable) {
                        newYearValue = dctYear + "";
                    }
                    // WITHOUT DOCUMENT CREATION TIME
                    else {
                        newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year");
                    }
                }

                // REPLACE THE UNDEF-YEAR WITH THE NEWLY CALCULATED YEAR AND ADD
                // TIMEX TO INDEXES
                if (newYearValue.equals("")) {
                    valueNew = valueNew.replaceFirst("UNDEF-year", "XXXX");
                } else {
                    valueNew = valueNew.replaceFirst("UNDEF-year", newYearValue);
                }
            }

            // /////////////////////////////////////////////////
            // just century is unspecified (UNDEF-century86) //
            // /////////////////////////////////////////////////
            else if ((valueNew.startsWith("UNDEF-century"))) {
                String newCenturyValue = dctCentury + "";
                int viThisDecade = Integer.parseInt(valueNew.substring(13, 14));
                if (dctAvailable) {
                    Logger.printDetail("dctCentury" + dctCentury);

                    newCenturyValue = dctCentury + "";
                    Logger.printDetail("dctCentury" + dctCentury);

                    // Tense is FUTURE
                    if ((last_used_tense.equals("FUTURE"))
                            || (last_used_tense.equals("PRESENTFUTURE"))) {
                        if (viThisDecade < dctDecade) {
                            newCenturyValue = dctCentury + 1 + "";
                        } else {
                            newCenturyValue = dctCentury + "";
                        }
                    }
                    // Tense is PAST
                    if ((last_used_tense.equals("PAST"))) {
                        if (dctDecade <= viThisDecade) {
                            newCenturyValue = dctCentury - 1 + "";
                        } else {
                            newCenturyValue = dctCentury + "";
                        }
                    }

                }
                // NARRATIVE DOCUMENTS
                else {
                    newCenturyValue = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "century");
                    if (viThisDecade > 5)
                        newCenturyValue = "19";
                    else
                        newCenturyValue = "20";

                }
                if (newCenturyValue.equals("")) {
                    // always assume that sixties, twenties, and so on are 19XX
                    // (changed 2011-09-08)
                    valueNew = valueNew.replaceFirst("UNDEF-century", "19");
                } else {
                    valueNew = valueNew.replaceFirst("UNDEF-century", newCenturyValue + "");
                }
                // always assume that sixties, twenties, and so on are 19XX
                // (changed 2011-09-08)
                if (valueNew.matches("\\d\\d\\dX")) {
                    valueNew = "19" + valueNew.substring(2);
                }
            }

            // //////////////////////////////////////////////////
            // CHECK IMPLICIT EXPRESSIONS STARTING WITH UNDEF //
            // //////////////////////////////////////////////////
            else if (valueNew.startsWith("UNDEF")) {
                //valueNew = value_i;

                // ////////////////
                // TO CALCULATE //
                // ////////////////
                // year to calculate
                if (valueNew
                        .matches("^UNDEF-(this|REFUNIT|REF)-(.*)-(MINUS|PLUS)-(.*)")
                        || t_i.getTimexType().indexOf("date_r19g") >= 0) {
                    for (Object mr : Toolbox.findMatches(
                            Pattern.compile("^(UNDEF-(this|REFUNIT|REF)-(.*?)-(MINUS|PLUS)-(.*))"),
                            valueNew)) {
                        String checkUndef = ((MatchResult) mr).group(1);
                        String ltn = ((MatchResult) mr).group(2);
                        String unit = ((MatchResult) mr).group(3);
                        String op = ((MatchResult) mr).group(4);
                        String lv = ((MatchResult) mr).group(5);

                        int pos = lv.indexOf("K");
                        if (pos >= 0) {
                            String v1 = lv.substring(0, pos);
                            String v2 = lv.substring(pos + 1);
                            int pos1 = v1.indexOf("F");
                            if (pos1 >= 0) {
                                String v11 = v1.substring(0, pos1);
                                String v12 = v1.substring(pos1 + 2);
                                double v = Double.parseDouble(v2)
                                        + Double.parseDouble(v11)
                                        / Double.parseDouble(v12);
                                lv = v + "";
                            } else {
                                double x = (Double.parseDouble(v2) + Double
                                        .parseDouble(v1)) / 2.0;
                                lv = x + "";
                            }
                        }
                        double ddiff = Double.parseDouble(lv);
                        int diff = (int) Math.round(ddiff);

                        // do the processing for SCIENTIFIC documents (TPZ
                        // identification could be improved)
                        String opSymbol = "-";
                        if (op.equals("PLUS")) {
                            opSymbol = "+";
                        }
                        if (unit.equals("year")) {
                            String diffString = diff + "";
                            if (diff < 10) {
                                diffString = "000" + diff;
                            } else if (diff < 100) {
                                diffString = "00" + diff;
                            } else if (diff < 1000) {
                                diffString = "0" + diff;
                            }
                            valueNew = "TPZ" + opSymbol + diffString;
                        } else if (unit.equals("month")) {
                            String diffString = diff + "";
                            if (diff < 10) {
                                diffString = "0000-0" + diff;
                            } else {
                                diffString = "0000-" + diff;
                            }
                            valueNew = "TPZ" + opSymbol + diffString;
                        } else if (unit.equals("week")) {
                            String diffString = diff + "";
                            if (diff < 10) {
                                diffString = "0000-W0" + diff;
                            } else {
                                diffString = "0000-W" + diff;
                            }
                            valueNew = "TPZ" + opSymbol + diffString;
                        } else if (unit.equals("day")) {
                            String diffString = diff + "";
                            if (diff < 10) {
                                diffString = "0000-00-0" + diff;
                            } else {
                                diffString = "0000-00-" + diff;
                            }
                            valueNew = "TPZ" + opSymbol + diffString;
                        } else if (unit.equals("hour")) {
                            String diffString = diff + "";
                            if (diff < 10) {
                                diffString = "0000-00-00T0" + diff;
                            } else {
                                diffString = "0000-00-00T" + diff;
                            }
                            valueNew = "TPZ" + opSymbol + diffString;
                        } else if (unit.equals("minute")) {
                            String diffString = diff + "";
                            if (diff < 10) {
                                diffString = "0000-00-00T00:0" + diff;
                            } else {
                                diffString = "0000-00-00T00:" + diff;
                            }
                            valueNew = "TPZ" + opSymbol + diffString;
                        } else if (unit.equals("second")) {
                            String diffString = diff + "";
                            if (diff < 10) {
                                diffString = "0000-00-00T00:00:0" + diff;
                            } else {
                                diffString = "0000-00-00T00:00:" + diff;
                            }
                            valueNew = "TPZ" + opSymbol + diffString;
                        }

                        // check for REFUNIT (only allowed for "year")
                        if ((ltn.equals("REFUNIT"))
                                && (unit.equals("year"))) {
                            String dateWithYear = ContextAnalyzer.getLastMentionedX(linearDates, i, "dateYear");
                            if (dateWithYear.equals("")) {
                                valueNew = valueNew.replace(checkUndef, "XXXX");
                            } else {
                                if (op.equals("MINUS")) {
                                    diff = diff * (-1);
                                }
                                int yearNew = Integer.parseInt(dateWithYear.substring(0, 4)) + diff;
                                String rest = dateWithYear.substring(4);
                                valueNew = valueNew.replace(checkUndef,
                                        yearNew + rest);
                            }
                        }

                        if (unit.equals("decade")) {
                            if (dctAvailable
                                    && ltn.equals("this")) {
                                int decade = dctDecade;
                                if (op.equals("MINUS")) {
                                    decade = dctDecade - diff;
                                } else if (op.equals("PLUS")) {
                                    decade = dctDecade + diff;
                                }
                                valueNew = valueNew.replace(checkUndef, decade + "X");
                            } else {
                                String lmDecade = ContextAnalyzer
                                        .getLastMentionedX(linearDates, i, "decade");
                                if (lmDecade.equals("")) {
                                    valueNew = valueNew.replace(checkUndef, "XXX");
                                } else {
                                    if (op.equals("MINUS")) {
                                        lmDecade = Integer.parseInt(lmDecade) - diff + "X";
                                    } else if (op.equals("PLUS")) {
                                        lmDecade = Integer.parseInt(lmDecade) + diff + "X";
                                    }
                                    valueNew = valueNew.replace(checkUndef, lmDecade);
                                }
                            }
                        } else if (unit.equals("year")) {
                            if (dctAvailable
                                    && ltn.equals("this")) {
                                int intValue = dctYear;
                                if (op.equals("MINUS")) {
                                    intValue = dctYear - diff;
                                } else if (op.equals("PLUS")) {
                                    intValue = dctYear + diff;
                                }
                                valueNew = valueNew.replace(checkUndef,
                                        intValue + "");
                            } else {
                                String lmYear = ContextAnalyzer.getLastMentionedX(linearDates, i, "year");
                                if (lmYear.equals("")) {
                                    valueNew = valueNew.replace(checkUndef, "XXXX");
                                } else {
                                    int intValue = Integer.parseInt(lmYear);
                                    if (op.equals("MINUS")) {
                                        intValue = Integer.parseInt(lmYear) - diff;
                                    } else if (op.equals("PLUS")) {
                                        intValue = Integer.parseInt(lmYear) + diff;
                                    }
                                    valueNew = valueNew.replace(checkUndef, intValue + "");
                                }
                            }
                        } else if (unit.equals("quarter")) {
                            if (dctAvailable
                                    && ltn.equals("this")) {
                                int intYear = dctYear;
                                int intQuarter = Integer
                                        .parseInt(dctQuarter.substring(1));
                                int diffQuarters = diff % 4;
                                diff = diff - diffQuarters;
                                int diffYears = diff / 4;
                                if (op.equals("MINUS")) {
                                    diffQuarters = diffQuarters * (-1);
                                    diffYears = diffYears * (-1);
                                }
                                intYear = intYear + diffYears;
                                intQuarter = intQuarter + diffQuarters;
                                valueNew = valueNew.replace(checkUndef,
                                        intYear + "-Q" + intQuarter);
                            } else {
                                String lmQuarter = ContextAnalyzer.getLastMentionedX(linearDates, i, "quarter");
                                if (lmQuarter.equals("")) {
                                    valueNew = valueNew.replace(checkUndef, "XXXX-XX");
                                } else {
                                    int intYear = Integer.parseInt(lmQuarter.substring(0, 4));
                                    int intQuarter = Integer.parseInt(lmQuarter.substring(6));
                                    int diffQuarters = diff % 4;
                                    diff = diff - diffQuarters;
                                    int diffYears = diff / 4;
                                    if (op.equals("MINUS")) {
                                        diffQuarters = diffQuarters * (-1);
                                        diffYears = diffYears * (-1);
                                    }
                                    intYear = intYear + diffYears;
                                    intQuarter = intQuarter + diffQuarters;
                                    valueNew = valueNew.replace(checkUndef, intYear + "-Q" + intQuarter);
                                }
                            }
                        } else if (unit.equals("month")) {
                            if (dctAvailable
                                    && ltn.equals("this")) {
                                if (op.equals("MINUS")) {
                                    diff = diff * (-1);
                                }
                                valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(dctYear + "-" + norm.getFromNormNumber(dctMonth + ""), diff));
                            } else if (t_i.getContextSentence().getCoveredText().indexOf("follow up") >= 0
                                    || t_i.getContextSentence().getCoveredText().indexOf("follow-up") >= 0
                                    || t_i.getContextSentence().getCoveredText().indexOf("followup") >= 0
                                    || t_i.getContextSentence().getCoveredText().indexOf("followed") >= 0
                                    || t_i.getContextSentence().getSegment().getId().equals("9")) {
                                String[] parts = valueNew.split("-");
                                if (parts[3].equals("PLUS")) {
                                    diff = (int) Math.round(ddiff * 30);
                                    valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(disdate.getTimexValue(), diff));
                                }
                            } else {
                                String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates, i, "month");
                                if (lmMonth.equals("")) {
                                    valueNew = valueNew.replace(checkUndef, "XXXX-XX");
                                } else {
                                    if (op.equals("MINUS")) {
                                        diff = diff * (-1);
                                    }
                                    valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(lmMonth, diff));
                                }
                            }
                        } else if (unit.equals("week")) {
                            if (dctAvailable
                                    && ltn.equals("this")) {
                                if (op.equals("MINUS")) {
                                    diff = (int) Math.round(ddiff * 7
                                            * (-1));
                                } else if (op.equals("PLUS")) {
                                    diff = (int) Math.round(ddiff * 7);
                                }
                                valueNew = valueNew
                                        .replace(
                                                checkUndef,
                                                DateCalculator
                                                        .getXNextDay(
                                                                dctYear
                                                                        + "-"
                                                                        + norm
                                                                        .getFromNormNumber(dctMonth
                                                                                + "")
                                                                        + "-"
                                                                        + dctDay,
                                                                diff));
                            } else if (t_i.getContextSentence()
                                    .getCoveredText().indexOf("follow up") >= 0
                                    || t_i.getContextSentence()
                                    .getCoveredText().indexOf(
                                            "follow-up") >= 0
                                    || t_i.getContextSentence()
                                    .getCoveredText().indexOf(
                                            "followup") >= 0
                                    || t_i.getContextSentence()
                                    .getCoveredText().indexOf(
                                            "followed") >= 0

                                    || t_i.getContextSentence().getSegment().getValue().equals(
                                    "follow_up")) {
                                String[] parts = valueNew.split("-");
                                if (parts[3].equals("PLUS")) {
                                    // diff=Integer.parseInt(parts[4])*7;
                                    diff = (int) Math.round(ddiff * 7);
                                    valueNew = valueNew
                                            .replace(
                                                    checkUndef,
                                                    DateCalculator
                                                            .getXNextDay(
                                                                    disdate
                                                                            .getTimexValue(),
                                                                    diff));
                                }
                            } else {
                                String lmDay = ContextAnalyzer
                                        .getLastMentionedX(linearDates, i,
                                                "day");
                                if (lmDay.equals("")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            "XXXX-XX-XX");
                                } else {
                                    if (op.equals("MINUS")) {
                                        diff = (int) Math.round(ddiff * 7
                                                * (-1));
                                    } else if (op.equals("PLUS")) {
                                        diff = (int) Math.round(ddiff * 7);
                                    }
                                    valueNew = valueNew.replace(checkUndef,
                                            DateCalculator.getXNextDay(
                                                    lmDay, diff));
                                }
                            }
                        } else if (unit.equals("day")) {
                            if (dctAvailable
                                    && ltn.equals("this")) {
                                if (op.equals("MINUS")) {
                                    diff = diff * (-1);
                                }
                                valueNew = valueNew
                                        .replace(
                                                checkUndef,
                                                DateCalculator
                                                        .getXNextDay(
                                                                dctYear
                                                                        + "-"
                                                                        + norm
                                                                        .getFromNormNumber(dctMonth
                                                                                + "")
                                                                        + "-"
                                                                        + dctDay,
                                                                diff));
                            } else if (t_i.getContextSentence()
                                    .getCoveredText().indexOf("follow up") >= 0
                                    || t_i.getContextSentence()
                                    .getCoveredText().indexOf(
                                            "follow-up") >= 0
                                    || t_i.getContextSentence()
                                    .getCoveredText().indexOf(
                                            "followup") >= 0
                                    || t_i.getContextSentence()
                                    .getCoveredText().indexOf(
                                            "followed") >= 0
                                    || t_i.getContextSentence().getSegment().getValue().equals(
                                    "follow_up")) {
                                String[] parts = valueNew.split("-");
                                if (parts.length >= 4)
                                    if (parts[3].equals("PLUS"))
                                        valueNew = DateCalculator.getXNextDay(
                                                disdate.getTimexValue(), diff);
                            } else if (t_i.getContextSentence().getSegment().getId().equals(
                                    "5.32")
                                    && t_i.getFoundByRule().equals("r19g5")) {
                                valueNew = DateCalculator.getXNextDay(
                                        admdate.getTimexValue(), 1);
                            } else {
                                String lmDay = ContextAnalyzer
                                        .getLastMentionedX(linearDates, i,
                                                "day");
                                if (lmDay.equals("")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            "XXXX-XX-XX");
                                } else {
                                    if (op.equals("MINUS")) {
                                        diff = diff * (-1);
                                    }
                                    valueNew = valueNew.replace(checkUndef,
                                            DateCalculator.getXNextDay(
                                                    lmDay, diff));
                                }
                            }
                        }
                    }
                }
            }


            // decade
            if (valueNew.startsWith("UNDEF-last-decade")) {
                String checkUndef = "UNDEF-last-decade";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef,
                            (dctYear - 10 + "").substring(0, 3) + "X");
                } else {
                    String lmDecade = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "decade");
                    if (lmDecade.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, Integer
                                .parseInt(lmDecade)
                                - 1 + "X");
                    }
                }
            } else if (valueNew.startsWith("UNDEF-this-decade")) {
                String checkUndef = "UNDEF-this-decade";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, (dctYear + "")
                            .substring(0, 3)
                            + "X");
                } else {
                    String lmDecade = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "decade");
                    if (lmDecade.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, lmDecade
                                + "X");
                    }
                }
            } else if (valueNew.startsWith("UNDEF-next-decade")) {
                String checkUndef = "UNDEF-next-decade";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef,
                            (dctYear + 10 + "").substring(0, 3) + "X");
                } else {
                    String lmDecade = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "decade");
                    if (lmDecade.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, Integer
                                .parseInt(lmDecade)
                                + 1 + "X");
                    }
                }
            }

            // year
            else if (valueNew.startsWith("UNDEF-last-year")) {
                String checkUndef = "UNDEF-last-year";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, dctYear - 1
                            + "");
                } else {
                    String lmYear = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "year");
                    if (lmYear.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, Integer
                                .parseInt(lmYear)
                                - 1 + "");
                    }
                }
            } else if (valueNew.startsWith("UNDEF-this-year")) {
                String checkUndef = "UNDEF-this-year";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, dctYear + "");
                } else {
                    String lmYear = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "year");
                    if (lmYear.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, lmYear);
                    }
                }
            } else if (valueNew.startsWith("UNDEF-next-year")) {
                String checkUndef = "UNDEF-next-year";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, dctYear + 1
                            + "");
                } else {
                    String lmYear = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "year");
                    if (lmYear.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, Integer
                                .parseInt(lmYear)
                                + 1 + "");
                    }
                }
            }

            // month
            else if (valueNew.startsWith("UNDEF-last-month")) {
                String checkUndef = "UNDEF-last-month";
                if (dctAvailable) {
                    valueNew = valueNew
                            .replace(
                                    checkUndef,
                                    DateCalculator
                                            .getXNextMonth(
                                                    dctYear
                                                            + "-"
                                                            + norm
                                                            .getFromNormNumber(dctMonth
                                                                    + ""),
                                                    -1));
                } else {
                    String lmMonth = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "month");
                    if (lmMonth.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-XX");
                    } else {
                        valueNew = valueNew.replace(checkUndef,
                                DateCalculator.getXNextMonth(lmMonth, -1));
                    }
                }
            } else if (valueNew.startsWith("UNDEF-this-month")) {
                String checkUndef = "UNDEF-this-month";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, dctYear + "-"
                            + norm.getFromNormNumber(dctMonth + ""));
                } else {
                    String lmMonth = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "month");
                    if (lmMonth.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-XX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, lmMonth);
                    }
                }
            } else if (valueNew.startsWith("UNDEF-next-month")) {
                String checkUndef = "UNDEF-next-month";
                if (dctAvailable) {
                    valueNew = valueNew
                            .replace(
                                    checkUndef,
                                    DateCalculator
                                            .getXNextMonth(
                                                    dctYear
                                                            + "-"
                                                            + norm
                                                            .getFromNormNumber(dctMonth
                                                                    + ""),
                                                    1));
                } else {
                    String lmMonth = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "month");
                    if (lmMonth.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-XX");
                    } else {
                        valueNew = valueNew.replace(checkUndef,
                                DateCalculator.getXNextMonth(lmMonth, 1));
                    }
                }
            }

            // day
            else if (valueNew.startsWith("UNDEF-last-day")) {
                String checkUndef = "UNDEF-last-day";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, DateCalculator
                            .getXNextDay(dctYear + "-"
                                    + norm.getFromNormNumber(dctMonth + "")
                                    + "-" + dctDay, -1));
                } else {
                    String lmDay = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "day");
                    if (lmDay.equals("")) {
                        valueNew = valueNew.replace(checkUndef,
                                "XXXX-XX-XX");
                    } else {
                        valueNew = valueNew.replace(checkUndef,
                                DateCalculator.getXNextDay(lmDay, -1));
                    }
                }
            } else if (valueNew.startsWith("UNDEF-this-day")) {
                String checkUndef = "UNDEF-this-day";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, dctYear + "-"
                            + norm.getFromNormNumber(dctMonth + "") + "-"
                            + norm.getFromNormNumber(dctDay + ""));
                } else {

                    // Hongfang Add
                    if (i + 1 < linearDates.size()
                            && linearDates.get(i + 1).getTimexType().equals("DATE")
                            && linearDates.get(i + 1).getContextSentence().getId() == t_i
                            .getContextSentence().getId()) {
                        valueNew = valueNew.replace(checkUndef, "UNDEF-NEXT-ENTRY");
                    }

                    if (i - 1 > 0
                            && linearDates.get(i - 1).getTimexType().equals("DATE")
                            && linearDates.get(i - 1).getContextSentence().getId() == t_i
                            .getContextSentence().getId()) {
                        valueNew = valueNew.replace(checkUndef, linearDates.get(i - 1).getTimexValue());
                    }
                    // end Hongfang Add

                    String lmDay = ContextAnalyzer.getLastMentionedX(linearDates, i, "day");
                    if (!lmDay.equals("")) {
                        valueNew = valueNew.replace(checkUndef, lmDay);
                    } else if (t_i.getContextSentence().getSegment().getValue().equals("history_present_illness")) {
                        valueNew = valueNew.replace(checkUndef, admdate.getTimexValue());
                    } else {
                        valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
                    }
                    // if (value_i.equals("UNDEF-this-day")) {
                    // valueNew = "PRESENT_REF";
                    // }
                }
            } else if (valueNew.startsWith("UNDEF-next-day")) {
                String checkUndef = "UNDEF-next-day";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, DateCalculator
                            .getXNextDay(dctYear + "-"
                                    + norm.getFromNormNumber(dctMonth + "")
                                    + "-" + dctDay, 1));
                } else {
                    String lmDay = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "day");
                    if (lmDay.equals("")) {
                        valueNew = valueNew.replace(checkUndef,
                                "XXXX-XX-XX");
                    } else {
                        valueNew = valueNew.replace(checkUndef,
                                DateCalculator.getXNextDay(lmDay, 1));
                    }
                }
            }

            // week
            else if (valueNew.startsWith("UNDEF-last-week")) {
                String checkUndef = "UNDEF-last-week";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, DateCalculator
                            .getXNextWeek(dctYear + "-W"
                                            + norm.getFromNormNumber(dctWeek + ""),
                                    -1));
                } else {
                    String lmWeek = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "week");
                    if (lmWeek.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-WXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef,
                                DateCalculator.getXNextWeek(lmWeek, -1));
                    }
                }
            } else if (valueNew.startsWith("UNDEF-this-week")) {
                String checkUndef = "UNDEF-this-week";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, dctYear + "-W"
                            + norm.getFromNormNumber(dctWeek + ""));
                } else {
                    String lmWeek = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "week");
                    if (lmWeek.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-WXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, lmWeek);
                    }
                }
            } else if (valueNew.startsWith("UNDEF-next-week")) {
                String checkUndef = "UNDEF-next-week";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, DateCalculator
                            .getXNextWeek(dctYear + "-W"
                                            + norm.getFromNormNumber(dctWeek + ""),
                                    1));
                } else {
                    String lmWeek = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "week");
                    if (lmWeek.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-WXX");
                    } else {
                        valueNew = valueNew.replace(checkUndef,
                                DateCalculator.getXNextWeek(lmWeek, 1));
                    }
                }
            }

            // quarter
            else if (valueNew.startsWith("UNDEF-last-quarter")) {
                String checkUndef = "UNDEF-last-quarter";
                if (dctAvailable) {
                    if (dctQuarter.equals("Q1")) {
                        valueNew = valueNew.replace(checkUndef, dctYear - 1
                                + "-Q4");
                    } else {
                        int newQuarter = Integer.parseInt(dctQuarter
                                .substring(1, 2)) - 1;
                        valueNew = valueNew.replace(checkUndef, dctYear
                                + "-Q" + newQuarter);
                    }
                } else {
                    String lmQuarter = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "quarter");
                    if (lmQuarter.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-QX");
                    } else {
                        int lmQuarterOnly = Integer.parseInt(lmQuarter
                                .substring(6, 7));
                        int lmYearOnly = Integer.parseInt(lmQuarter
                                .substring(0, 4));
                        if (lmQuarterOnly == 1) {
                            valueNew = valueNew.replace(checkUndef,
                                    lmYearOnly - 1 + "-Q4");
                        } else {
                            int newQuarter = lmQuarterOnly - 1;
                            valueNew = valueNew.replace(checkUndef, dctYear
                                    + "-Q" + newQuarter);
                        }
                    }
                }
            } else if (valueNew.startsWith("UNDEF-this-quarter")) {
                String checkUndef = "UNDEF-this-quarter";
                if (dctAvailable) {
                    valueNew = valueNew.replace(checkUndef, dctYear + "-"
                            + dctQuarter);
                } else {
                    String lmQuarter = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "quarter");
                    if (lmQuarter.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-QX");
                    } else {
                        valueNew = valueNew.replace(checkUndef, lmQuarter);
                    }
                }
            } else if (valueNew.startsWith("UNDEF-next-quarter")) {
                String checkUndef = "UNDEF-next-quarter";
                if (dctAvailable) {
                    if (dctQuarter.equals("Q4")) {
                        valueNew = valueNew.replace(checkUndef, dctYear + 1
                                + "-Q1");
                    } else {
                        int newQuarter = Integer.parseInt(dctQuarter
                                .substring(1, 2)) + 1;
                        valueNew = valueNew.replace(checkUndef, dctYear
                                + "-Q" + newQuarter);
                    }
                } else {
                    String lmQuarter = ContextAnalyzer.getLastMentionedX(
                            linearDates, i, "quarter");
                    if (lmQuarter.equals("")) {
                        valueNew = valueNew.replace(checkUndef, "XXXX-QX");
                    } else {
                        int lmQuarterOnly = Integer.parseInt(lmQuarter
                                .substring(6, 7));
                        int lmYearOnly = Integer.parseInt(lmQuarter
                                .substring(0, 4));
                        if (lmQuarterOnly == 4) {
                            valueNew = valueNew.replace(checkUndef,
                                    lmYearOnly + 1 + "-Q1");
                        } else {
                            int newQuarter = lmQuarterOnly + 1;
                            valueNew = valueNew.replace(checkUndef, dctYear
                                    + "-Q" + newQuarter);
                        }
                    }
                }
            }

            // MONTH NAMES
            else if (valueNew
                    .matches("UNDEF-(last|this|next)-(january|february|march|april|may|june|july|august|september|october|november|december).*")) {
                for (Object mr : Toolbox
                        .findMatches(
                                Pattern
                                        .compile("(UNDEF-(last|this|next)-(january|february|march|april|may|june|july|august|september|october|november|december)).*"),
                                valueNew)) {
                    String checkUndef = ((MatchResult) mr).group(1);
                    String ltn = ((MatchResult) mr).group(2);
                    String newMonth = norm.getFromNormMonthName(((MatchResult) mr).group(3));
                    int newMonthInt = Integer.parseInt(newMonth);
                    if (ltn.equals("last")) {
                        if (dctAvailable) {
                            if (dctMonth <= newMonthInt) {
                                valueNew = valueNew.replace(checkUndef,
                                        dctYear - 1 + "-" + newMonth);
                            } else {
                                valueNew = valueNew.replace(checkUndef,
                                        dctYear + "-" + newMonth);
                            }
                        } else {
                            String lmMonth = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "month");
                            if (lmMonth.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX");
                            } else {
                                int lmMonthInt = Integer.parseInt(lmMonth
                                        .substring(5, 7));
                                if (lmMonthInt <= newMonthInt) {
                                    valueNew = valueNew.replace(checkUndef,
                                            Integer.parseInt(lmMonth
                                                    .substring(0, 4))
                                                    - 1 + "-" + newMonth);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            lmMonth.substring(0, 4) + "-"
                                                    + newMonth);
                                }
                            }
                        }
                    } else if (ltn.equals("this")) {
                        if (dctAvailable) {
                            valueNew = valueNew.replace(checkUndef, dctYear
                                    + "-" + newMonth);
                        } else {
                            String lmMonth = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "month");
                            if (lmMonth.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX");
                            } else {
                                valueNew = valueNew.replace(checkUndef,
                                        lmMonth.substring(0, 4) + "-"
                                                + newMonth);
                            }
                        }
                    } else if (ltn.equals("next")) {
                        if (dctAvailable) {
                            if (dctMonth >= newMonthInt) {
                                valueNew = valueNew.replace(checkUndef,
                                        dctYear + 1 + "-" + newMonth);
                            } else {
                                valueNew = valueNew.replace(checkUndef,
                                        dctYear + "-" + newMonth);
                            }
                        } else {
                            String lmMonth = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "month");
                            if (lmMonth.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX");
                            } else {
                                int lmMonthInt = Integer.parseInt(lmMonth
                                        .substring(5, 7));
                                if (lmMonthInt >= newMonthInt) {
                                    valueNew = valueNew.replace(checkUndef,
                                            Integer.parseInt(lmMonth
                                                    .substring(0, 4))
                                                    + 1 + "-" + newMonth);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            lmMonth.substring(0, 4) + "-"
                                                    + newMonth);
                                }
                            }
                        }
                    }
                }
            }

            // SEASONS NAMES
            else if (valueNew
                    .matches("^UNDEF-(last|this|next)-(SP|SU|FA|WI).*")) {
                for (Object mr : Toolbox
                        .findMatches(
                                Pattern
                                        .compile("(UNDEF-(last|this|next)-(SP|SU|FA|WI)).*"),
                                valueNew)) {
                    String checkUndef = ((MatchResult) mr).group(1);
                    String ltn = ((MatchResult) mr).group(2);
                    String newSeason = ((MatchResult) mr).group(3);
                    if (ltn.equals("last")) {
                        if (dctAvailable) {
                            if (dctSeason.equals("SP")) {
                                valueNew = valueNew.replace(checkUndef,
                                        dctYear - 1 + "-" + newSeason);
                            } else if (dctSeason.equals("SU")) {
                                if (newSeason.equals("SP")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + "-" + newSeason);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear - 1 + "-" + newSeason);
                                }
                            } else if (dctSeason.equals("FA")) {
                                if ((newSeason.equals("SP"))
                                        || (newSeason.equals("SU"))) {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + "-" + newSeason);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear - 1 + "-" + newSeason);
                                }
                            } else if (dctSeason.equals("WI")) {
                                if (newSeason.equals("WI")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear - 1 + "-" + newSeason);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + "-" + newSeason);
                                }
                            }
                        } else { // NARRATVIE DOCUMENT
                            String lmSeason = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "season");
                            if (lmSeason.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX");
                            } else {
                                if (lmSeason.substring(5, 7).equals("SP")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            Integer.parseInt(lmSeason
                                                    .substring(0, 4))
                                                    - 1 + "-" + newSeason);
                                } else if (lmSeason.substring(5, 7).equals(
                                        "SU")) {
                                    if (lmSeason.substring(5, 7).equals(
                                            "SP")) {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + "-" + newSeason);
                                    } else {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        - 1
                                                        + "-"
                                                        + newSeason);
                                    }
                                } else if (lmSeason.substring(5, 7).equals(
                                        "FA")) {
                                    if ((newSeason.equals("SP"))
                                            || (newSeason.equals("SU"))) {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + "-" + newSeason);
                                    } else {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        - 1
                                                        + "-"
                                                        + newSeason);
                                    }
                                } else if (lmSeason.substring(5, 7).equals(
                                        "WI")) {
                                    if (newSeason.equals("WI")) {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        - 1
                                                        + "-"
                                                        + newSeason);
                                    } else {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + "-" + newSeason);
                                    }
                                }
                            }
                        }
                    } else if (ltn.equals("this")) {
                        if (dctAvailable) {
                            // TODO include tense of sentence?
                            valueNew = valueNew.replace(checkUndef, dctYear
                                    + "-" + newSeason);
                        } else {
                            // TODO include tense of sentence?
                            String lmSeason = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "season");
                            if (lmSeason.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX");
                            } else {
                                valueNew = valueNew.replace(checkUndef,
                                        lmSeason.substring(0, 4) + "-"
                                                + newSeason);
                            }
                        }
                    } else if (ltn.equals("next")) {
                        if (dctAvailable) {
                            if (dctSeason.equals("SP")) {
                                if (newSeason.equals("SP")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + 1 + "-" + newSeason);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + "-" + newSeason);
                                }
                            } else if (dctSeason.equals("SU")) {
                                if ((newSeason.equals("SP"))
                                        || (newSeason.equals("SU"))) {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + 1 + "-" + newSeason);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + "-" + newSeason);
                                }
                            } else if (dctSeason.equals("FA")) {
                                if (newSeason.equals("WI")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + "-" + newSeason);
                                } else {
                                    valueNew = valueNew.replace(checkUndef,
                                            dctYear + 1 + "-" + newSeason);
                                }
                            } else if (dctSeason.equals("WI")) {
                                valueNew = valueNew.replace(checkUndef,
                                        dctYear + 1 + "-" + newSeason);
                            }
                        } else { // NARRATIVE DOCUMENT
                            String lmSeason = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "season");
                            if (lmSeason.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX");
                            } else {
                                if (lmSeason.substring(5, 7).equals("SP")) {
                                    if (newSeason.equals("SP")) {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + 1
                                                        + "-"
                                                        + newSeason);
                                    } else {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + "-" + newSeason);
                                    }
                                } else if (lmSeason.substring(5, 7).equals(
                                        "SU")) {
                                    if ((newSeason.equals("SP"))
                                            || (newSeason.equals("SU"))) {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + 1
                                                        + "-"
                                                        + newSeason);
                                    } else {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + "-" + newSeason);
                                    }
                                } else if (lmSeason.substring(5, 7).equals(
                                        "FA")) {
                                    if (newSeason.equals("WI")) {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + "-" + newSeason);
                                    } else {
                                        valueNew = valueNew.replace(
                                                checkUndef, Integer
                                                        .parseInt(lmSeason
                                                                .substring(
                                                                        0,
                                                                        4))
                                                        + 1
                                                        + "-"
                                                        + newSeason);
                                    }
                                } else if (lmSeason.substring(5, 7).equals(
                                        "WI")) {
                                    valueNew = valueNew.replace(checkUndef,
                                            Integer.parseInt(lmSeason
                                                    .substring(0, 4))
                                                    + 1 + "-" + newSeason);
                                }
                            }
                        }
                    }
                }
            }

            // WEEKDAY NAMES
            // TODO the calculation is strange, but works
            // TODO tense should be included?!
            else if (valueNew
                    .matches("^UNDEF-(last|this|next|day)-(monday|tuesday|wednesday|thursday|friday|saturday|sunday).*")) {
                for (Object mr : Toolbox
                        .findMatches(
                                Pattern
                                        .compile("(UNDEF-(last|this|next|day)-(monday|tuesday|wednesday|thursday|friday|saturday|sunday)).*"),
                                valueNew)) {
                    String checkUndef = ((MatchResult) mr).group(1);
                    String ltnd = ((MatchResult) mr).group(2);
                    String newWeekday = ((MatchResult) mr).group(3);
                    int newWeekdayInt = Integer.parseInt(norm
                            .getFromNormDayInWeek(newWeekday));
                    if (ltnd.equals("last")) {
                        if (dctAvailable) {
                            int diff = (-1) * (dctWeekday - newWeekdayInt);
                            if (diff >= 0) {
                                diff = diff - 7;
                            }
                            valueNew = valueNew.replace(checkUndef,
                                    DateCalculator.getXNextDay(
                                            dctYear + "-" + dctMonth + "-"
                                                    + dctDay, diff));
                        } else {
                            String lmDay = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "day");
                            if (lmDay.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX-XX");
                            } else {
                                int lmWeekdayInt = DateCalculator
                                        .getWeekdayOfDate(lmDay);
                                int diff = (-1)
                                        * (lmWeekdayInt - newWeekdayInt);
                                if (diff >= 0) {
                                    diff = diff - 7;
                                }
                                valueNew = valueNew.replace(checkUndef,
                                        DateCalculator.getXNextDay(lmDay,
                                                diff));
                            }
                        }
                    } else if (ltnd.equals("this")) {
                        if (dctAvailable) {
                            // TODO tense should be included?!
                            int diff = (-1) * (dctWeekday - newWeekdayInt);
                            if (diff >= 0) {
                                diff = diff - 7;
                            }
                            if (diff == -7) {
                                diff = 0;
                            }

                            valueNew = valueNew.replace(checkUndef,
                                    DateCalculator.getXNextDay(
                                            dctYear + "-" + dctMonth + "-"
                                                    + dctDay, diff));
                        } else {
                            // TODO tense should be included?!
                            String lmDay = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "day");
                            if (lmDay.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX-XX");
                            } else {
                                int lmWeekdayInt = DateCalculator
                                        .getWeekdayOfDate(lmDay);
                                int diff = (-1)
                                        * (lmWeekdayInt - newWeekdayInt);
                                if (diff >= 0) {
                                    diff = diff - 7;
                                }
                                if (diff == -7) {
                                    diff = 0;
                                }
                                valueNew = valueNew.replace(checkUndef,
                                        DateCalculator.getXNextDay(lmDay,
                                                diff));
                            }
                        }
                    } else if (ltnd.equals("next")) {
                        if (dctAvailable) {
                            int diff = newWeekdayInt - dctWeekday;
                            if (diff <= 0) {
                                diff = diff + 7;
                            }
                            valueNew = valueNew.replace(checkUndef,
                                    DateCalculator.getXNextDay(
                                            dctYear + "-" + dctMonth + "-"
                                                    + dctDay, diff));
                        } else {
                            String lmDay = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "day");
                            if (lmDay.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX-XX");
                            } else {
                                int lmWeekdayInt = DateCalculator
                                        .getWeekdayOfDate(lmDay);
                                int diff = newWeekdayInt - lmWeekdayInt;
                                if (diff <= 0) {
                                    diff = diff + 7;
                                }
                                valueNew = valueNew.replace(checkUndef,
                                        DateCalculator.getXNextDay(lmDay,
                                                diff));
                            }
                        }
                    } else if (ltnd.equals("day")) {
                        if (dctAvailable) {
                            // TODO tense should be included?!
                            int diff = (-1) * (dctWeekday - newWeekdayInt);
                            if (diff >= 0) {
                                diff = diff - 7;
                            }
                            if (diff == -7) {
                                diff = 0;
                            }
                            // Tense is FUTURE
                            if ((last_used_tense.equals("FUTURE"))
                                    || (last_used_tense
                                    .equals("PRESENTFUTURE"))) {
                                diff = diff + 7;
                            }
                            // Tense is PAST
                            if ((last_used_tense.equals("PAST"))) {

                            }
                            valueNew = valueNew.replace(checkUndef,
                                    DateCalculator.getXNextDay(
                                            dctYear + "-" + dctMonth + "-"
                                                    + dctDay, diff));
                        } else {
                            // TODO tense should be included?!
                            String lmDay = ContextAnalyzer
                                    .getLastMentionedX(linearDates, i,
                                            "day");
                            if (lmDay.equals("")) {
                                valueNew = valueNew.replace(checkUndef,
                                        "XXXX-XX-XX");
                            } else {
                                int lmWeekdayInt = DateCalculator
                                        .getWeekdayOfDate(lmDay);
                                int diff = (-1)
                                        * (lmWeekdayInt - newWeekdayInt);
                                if (diff >= 0) {
                                    diff = diff - 7;
                                }
                                if (diff == -7) {
                                    diff = 0;
                                }
                                valueNew = valueNew.replace(checkUndef,
                                        DateCalculator.getXNextDay(lmDay,
                                                diff));
                            }
                        }
                    }
                }

            } else {
                Logger
                        .printDetail(
                                component,
                                "ATTENTION: UNDEF value for: "
                                        + valueNew
                                        + " is not handled in disambiguation phase!");
            }


            if (valueNew.equals(t_i.getTimexValue()))
                changed = true;
            t_i.removeFromIndexes();
            Logger.printDetail(t_i.getTimexId()
                    + " DISAMBIGUATION PHASE: foundBy:" + t_i.getFoundByRule()
                    + " text:" + t_i.getCoveredText() + " value:"
                    + t_i.getTimexValue() + " NEW value:" + valueNew);
            t_i.setTimexValue(valueNew);
            if (t_i.getFoundByRule().indexOf("date_r19g") >= 0) {
                t_i.setTimexMod("APPROX");
            }
            t_i.addToIndexes();
            linearDates.set(i, t_i);
            // Hongfang handle undef for previous entry
            if (i - 1 >= 0
                    && linearDates.get(i - 1).getTimexValue().indexOf("UNDEF-NEXT-ENTRY") >= 0) {
                MedTimex3 t_previous = linearDates.get(i - 1);
                String oldValue = t_previous.getTimexValue();
                t_previous.removeFromIndexes();
                t_previous.setTimexValue(oldValue.replaceFirst("UNDEF-NEXT-ENTRY", valueNew));
                t_previous.addToIndexes();
                linearDates.set(i - 1, t_previous);
            }
        }
        return changed;
    }


    /**
     * Identify the part of speech (POS) of a MarchResult.
     *
     * @param tokBegin
     * @param tokEnd
     * @param s
     * @param jcas
     * @return
     */

    public String getPosFromMatchResult(int tokBegin, int tokEnd, Sentence s,
                                        JCas jcas) {
        // get all tokens in sentence
        HashMap<Integer, BaseToken> hmTokens = new HashMap<Integer, BaseToken>();
        FSIterator<? extends Annotation> iterTok = jcas.getAnnotationIndex(BaseToken.type).subiterator(s);
        while (iterTok.hasNext()) {
            BaseToken token = (BaseToken) iterTok.next();
            hmTokens.put(token.getBegin(), token);
        }
        // get correct token
        String pos = "";
        if (hmTokens.containsKey(tokBegin)) {
            BaseToken tokenToCheck = hmTokens.get(tokBegin);
            pos = tokenToCheck.getPartOfSpeech();
        }
        return pos;
    }

    /**
     * Apply the extraction rules, normalization rules
     *
     * @param timexType
     * @param hmPattern
     * @param hmOffset
     * @param hmNormalization
     * @param hmQuant
     * @param s
     * @param jcas
     */
    public boolean findTimexes(String timexType,
                               HashMap<Pattern, String> hmPattern,
                               HashMap<String, String> hmOffset,
                               HashMap<String, String> hmNormalization,
                               HashMap<String, String> hmQuant, Sentence s, JCas jcas) {
        boolean added = false;
        RuleManager rm = RuleManager.getInstance();
        HashMap<String, String> hmDatePosConstraint = rm
                .getHmDatePosConstraint();
        HashMap<String, String> hmDurationPosConstraint = rm
                .getHmDurationPosConstraint();
        HashMap<String, String> hmTimePosConstraint = rm
                .getHmTimePosConstraint();
        HashMap<String, String> hmSetPosConstraint = rm.getHmSetPosConstraint();

        // Iterator over the rules by sorted by the name of the rules
        // this is important since later, the timexId will be used to
        // decide which of two expressions shall be removed if both
        // have the same offset
        for (Iterator<Pattern> i = Toolbox.sortByValue(hmPattern).iterator(); i
                .hasNext(); ) {
            Pattern p = (Pattern) i.next();
            String sen = s.getCoveredText();
            if (lowerCase) sen = sen.toLowerCase();
            for (Object o : Toolbox.findMatches(p, sen)) {
                MatchResult mr = (MatchResult) o;
                boolean infrontBehindOK = ContextAnalyzer.checkInfrontBehind(mr,
                        s);

                boolean posConstraintOK = true;
                // CHECK POS CONSTRAINTS
                if (timexType.equals("DATE")) {
                    if (hmDatePosConstraint.containsKey(hmPattern.get(p))) {
                        posConstraintOK = checkPosConstraint(s,
                                hmDatePosConstraint.get(hmPattern.get(p)), mr,
                                jcas);
                    }
                } else if (timexType.equals("DURATION")) {
                    if (hmDurationPosConstraint.containsKey(hmPattern.get(p))) {
                        posConstraintOK = checkPosConstraint(s,
                                hmDurationPosConstraint.get(hmPattern.get(p)),
                                mr, jcas);
                    }
                } else if (timexType.equals("TIME")) {
                    if (hmTimePosConstraint.containsKey(hmPattern.get(p))) {
                        posConstraintOK = checkPosConstraint(s,
                                hmTimePosConstraint.get(hmPattern.get(p)), mr,
                                jcas);
                    }
                } else if (timexType.equals("SET")) {
                    if (hmSetPosConstraint.containsKey(hmPattern.get(p))) {
                        posConstraintOK = checkPosConstraint(s,
                                hmSetPosConstraint.get(hmPattern.get(p)), mr,
                                jcas);
                    }
                }

                if ((infrontBehindOK == true) && (posConstraintOK == true)) {

                    // Offset of timex expression (in the checked sentence)
                    int timexStart = mr.start();
                    int timexEnd = mr.end();

                    // Normalization from Files:

                    // Any offset parameter?
                    if (hmOffset.containsKey(hmPattern.get(p))) {
                        String offset = hmOffset.get(hmPattern.get(p));

                        // pattern for offset information
                        Pattern paOffset = Pattern
                                .compile("group\\(([0-9]+)\\)-group\\(([0-9]+)\\)");
                        for (Object lo : Toolbox.findMatches(paOffset,
                                offset)) {
                            MatchResult lmr = (MatchResult) lo;
                            int startOffset = Integer.parseInt(lmr.group(1));
                            int endOffset = Integer.parseInt(lmr.group(2));
                            timexStart = mr.start(startOffset);
                            timexEnd = mr.end(endOffset);
                        }
                    }

                    // Normalization Parameter
                    if (hmNormalization.containsKey(hmPattern.get(p))) {
                        String[] attributes = new String[4];
                        if (timexType.equals("DATE")) {
                            attributes = getAttributesForTimexFromFile(
                                    hmPattern.get(p), rm
                                            .getHmDateNormalization(), rm
                                            .getHmDateQuant(), rm
                                            .getHmDateFreq(),
                                    rm.getHmDateMod(), mr, jcas);
                            // System.out.println(attributes);
                        } else if (timexType.equals("DURATION")) {
                            attributes = getAttributesForTimexFromFile(
                                    hmPattern.get(p), rm
                                            .getHmDurationNormalization(), rm
                                            .getHmDurationQuant(), rm
                                            .getHmDurationFreq(), rm
                                            .getHmDurationMod(), mr, jcas);
                        } else if (timexType.equals("TIME")) {
                            attributes = getAttributesForTimexFromFile(
                                    hmPattern.get(p), rm
                                            .getHmTimeNormalization(), rm
                                            .getHmTimeQuant(), rm
                                            .getHmTimeFreq(),
                                    rm.getHmTimeMod(), mr, jcas);
                        } else if (timexType.equals("SET")) {
                            attributes = getAttributesForTimexFromFile(
                                    hmPattern.get(p), rm
                                            .getHmSetNormalization(), rm
                                            .getHmSetQuant(),
                                    rm.getHmSetFreq(), rm.getHmSetMod(), mr,
                                    jcas);
                        }
                        addTimexAnnotation(timexType,
                                timexStart + s.getBegin(), timexEnd
                                        + s.getBegin(), s, attributes[0],
                                attributes[1], attributes[2], attributes[3],
                                "t" + timexID++, hmPattern.get(p), jcas);
                        added = true;
                    } else {
                        Logger.printError("SOMETHING REALLY WRONG HERE: "
                                + hmPattern.get(p));
                    }
                }
            }
        }
        return added;
    }

    /**
     * Check whether the part of speech constraint defined in a rule is
     * satisfied.
     *
     * @param s
     * @param posConstraint
     * @param m
     * @param jcas
     * @return
     */
    public boolean checkPosConstraint(Sentence s, String posConstraint,
                                      MatchResult m, JCas jcas) {
        Pattern paConstraint = Pattern.compile("group\\(([0-9]+)\\):(.*?):");
        for (Object o : Toolbox.findMatches(paConstraint, posConstraint)) {
            MatchResult mr = (MatchResult) o;
            int groupNumber = Integer.parseInt(mr.group(1));
            int tokenBegin = s.getBegin() + m.start(groupNumber);
            int tokenEnd = s.getBegin() + m.end(groupNumber);
            String pos = mr.group(2);
            String pos_as_is = getPosFromMatchResult(tokenBegin, tokenEnd, s,
                    jcas);
            if (pos.equals(pos_as_is)) {
                Logger.printDetail("POS CONSTRAINT IS VALID: pos should be "
                        + pos + " and is " + pos_as_is);
            } else {
                return false;
            }
        }
        return true;
    }

    public String applyRuleFunctions(String tonormalize, MatchResult m) {
        NormalizationManager norm = NormalizationManager.getInstance();

        String normalized = "";
        // pattern for normalization functions + group information
        // pattern for group information
        Pattern paNorm = Pattern.compile("%([A-Za-z0-9]+?)\\(group\\(([0-9]+)\\)\\)");
        Pattern paGroup = Pattern.compile("group\\(([0-9]+)\\)");
        while ((tonormalize.contains("%")) || (tonormalize.contains("group"))) {
            // replace normalization functions
            for (Object mr : Toolbox.findMatches(paNorm, tonormalize)) {
                Logger.printDetail("-----------------------------------");
                Logger.printDetail("DEBUGGING: tonormalize:" + tonormalize);
                Logger.printDetail("DEBUGGING: ((MatchResult) mr).group():" + ((MatchResult) mr).group());
                Logger.printDetail("DEBUGGING: ((MatchResult) mr).group(1):" + ((MatchResult) mr).group(1));
                Logger.printDetail("DEBUGGING: ((MatchResult) mr).group(2):" + ((MatchResult) mr).group(2));
                Logger.printDetail("DEBUGGING: m.group():" + m.group());
                Logger.printDetail("DEBUGGING: m.group("
                        + Integer.parseInt(((MatchResult) mr).group(2)) + "):"
                        + m.group(Integer.parseInt(((MatchResult) mr).group(2))));
                Logger.printDetail("DEBUGGING: hmR...:"
                        + norm.getFromHmAllNormalization(((MatchResult) mr).group(1)).get(
                        m.group(Integer.parseInt(((MatchResult) mr).group(2)))));
                Logger.printDetail("-----------------------------------");

                if (!(m.group(Integer.parseInt(((MatchResult) mr).group(2))) == null)) {
                    String partToReplace = m.group(
                            Integer.parseInt(((MatchResult) mr).group(2))).replaceAll(
                            "[\n\\s]+", " ");
                    if (!(norm.getFromHmAllNormalization(((MatchResult) mr).group(1))
                            .containsKey(partToReplace))) {
                        Logger
                                .printDetail("Maybe problem with normalization of the resource: "
                                        + ((MatchResult) mr).group(1));
                        Logger
                                .printDetail("Maybe problem with part to replace? "
                                        + partToReplace);
                    }
                    // System.out.println(((MatchResult) mr).group()+" "+partToReplace+((MatchResult) mr).group(1));
                    tonormalize = tonormalize.replace(((MatchResult) mr).group(), (String) norm
                            .getFromHmAllNormalization(((MatchResult) mr).group(1)).get(
                                    partToReplace));
                } else {
                    Logger.printDetail("Empty part to normalize in "
                            + ((MatchResult) mr).group(1));

                    tonormalize = tonormalize.replace(((MatchResult) mr).group(), "");
                }
            }
            // replace other groups
            for (Object mr : Toolbox.findMatches(paGroup, tonormalize)) {
                Logger.printDetail("-----------------------------------");
                Logger.printDetail("DEBUGGING: tonormalize:" + tonormalize);
                Logger.printDetail("DEBUGGING: ((MatchResult) mr).group():" + ((MatchResult) mr).group());
                Logger.printDetail("DEBUGGING: ((MatchResult) mr).group(1):" + ((MatchResult) mr).group(1));
                Logger.printDetail("DEBUGGING: m.group():" + m.group());
                Logger.printDetail("DEBUGGING: m.group("
                        + Integer.parseInt(((MatchResult) mr).group(1)) + "):"
                        + m.group(Integer.parseInt(((MatchResult) mr).group(1))));
                Logger.printDetail("-----------------------------------");

                tonormalize = tonormalize.replace(((MatchResult) mr).group(), m.group(Integer
                        .parseInt(((MatchResult) mr).group(1))));
            }
            // replace substrings
            Pattern paSubstring = Pattern.compile("%SUBSTRING%\\((.*?),([0-9]+),([0-9]+)\\)");
            for (Object mr : Toolbox.findMatches(paSubstring, tonormalize)) {
                String substring = ((MatchResult) mr).group(1).substring(
                        Integer.parseInt(((MatchResult) mr).group(2)),
                        Integer.parseInt(((MatchResult) mr).group(3)));
                tonormalize = tonormalize.replace(((MatchResult) mr).group(), substring);
            }
            // replace lowercase
            Pattern paLowercase = Pattern.compile("%LOWERCASE%\\((.*?)\\)");
            for (Object mr : Toolbox.findMatches(paLowercase, tonormalize)) {
                String substring = ((MatchResult) mr).group(1).toLowerCase();
                tonormalize = tonormalize.replace(((MatchResult) mr).group(), substring);
            }
            // replace uppercase
            Pattern paUppercase = Pattern.compile("%UPPERCASE%\\((.*?)\\)");
            for (Object mr : Toolbox.findMatches(paUppercase, tonormalize)) {
                String substring = ((MatchResult) mr).group(1).toUpperCase();
                tonormalize = tonormalize.replace(((MatchResult) mr).group(), substring);
            }
            // replace sum, concatenation
            Pattern paSum = Pattern.compile("%SUM%\\((.*?),(.*?)\\)");
            for (Object mr : Toolbox.findMatches(paSum, tonormalize)) {
                String first = ((MatchResult) mr).group(1);
                String second = ((MatchResult) mr).group(1);
                if (first.matches("-?[0-9]+") && second.matches("-?[0-9]+")) {
                    int newValue = Integer.parseInt(first)
                            + Integer.parseInt(second);
                    tonormalize = tonormalize.replace(((MatchResult) mr).group(), newValue + "");
                } else {
                    Logger.printError("Attempted to sum " + first + " and " + second + ", one or both of which was not a number!");
                    tonormalize = tonormalize.replace(((MatchResult) mr).group(), "");
                }
            }
            // replace normalization function without group
            Pattern paNormNoGroup = Pattern.compile("%([A-Za-z0-9]+?)\\((.*?)\\)");
            for (Object mr : Toolbox.findMatches(paNormNoGroup, tonormalize)) {
                tonormalize = tonormalize.replace(((MatchResult) mr).group(), (String) norm
                        .getFromHmAllNormalization(((MatchResult) mr).group(1))
                        .get(((MatchResult) mr).group(2)));
            }
        }
        normalized = tonormalize;
        return normalized;
    }

    public String[] getAttributesForTimexFromFile(String rule,
                                                  HashMap<String, String> hmNormalization,
                                                  HashMap<String, String> hmQuant, HashMap<String, String> hmFreq,
                                                  HashMap<String, String> hmMod, MatchResult m, JCas jcas) {
        String[] attributes = new String[4];
        String value = "";
        String quant = "";
        String freq = "";
        String mod = "";

        // Normalize Value
        String value_normalization_pattern = hmNormalization.get(rule);
        value = applyRuleFunctions(value_normalization_pattern, m);

        // get quant
        if (hmQuant.containsKey(rule)) {
            String quant_normalization_pattern = hmQuant.get(rule);
            quant = applyRuleFunctions(quant_normalization_pattern, m);
        }

        // get freq
        if (hmFreq.containsKey(rule)) {
            String freq_normalization_pattern = hmFreq.get(rule);
            freq = applyRuleFunctions(freq_normalization_pattern, m);
        }

        // get mod
        if (hmMod.containsKey(rule)) {
            String mod_normalization_pattern = hmMod.get(rule);
            mod = applyRuleFunctions(mod_normalization_pattern, m);
        }

        // For example "P24H" -> "P1D"
        value = Toolbox.correctDurationValue(value);

        attributes[0] = value;
        attributes[1] = quant;
        attributes[2] = freq;
        attributes[3] = mod;

        return attributes;
    }
}