package edu.mayo.bsi.nlp2fhir;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.Element;
import org.hl7.fhir.UnitsOfTime;
import org.sqlite.SQLiteConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

/**
 * Utility class containing various useful shared methods
 */
public class Util {
    /**
     * Converts various accepted inputs into a FHIR format Unit of Time
     *
     * @param input The input untransformed string
     * @return A Unit of Time formatted string
     */
    public static String transformUnitOfTime(String input) {
        input = input.toLowerCase();
        if (input.endsWith("day") && input.length() > 3) { // Mon-Sun
            return "wk";
        }
        if (input.length() > 1) {
            switch (input.substring(0, 2)) {
                case "ho":
                    return "h";
                case "da":
                    return "d";
                case "mo":
                    return "mo";
                case "mi":
                    return "min";
                case "ye":
                    return "a";
                case "we":
                case "wk":
                    return "wk";
                case "se":
                    return "s";
            }
        } else {
            switch (input) {
                case "d":
                    return "d";
                case "m":
                    return "min";
                case "y":
                case "a":
                    return "a";
                case "s":
                    return "s";
                case "h":
                    return "h";
                case "w":
                    return "wk";
            }
        }
        return null;
    }

    public static String normalizeNumber(String input) {
        // Process divisions
        if (input.contains("/")) {
            String[] split = input.split("/");
            double[] values = new double[split.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = Double.valueOf(normalizeNumber(split[i]));
            }
            double val = values[0];
            for (int i = 1; i < split.length; i++) {
                val /= values[i]; // TODO this is naive/doesn't respect order of operations (should NOT be a concern, but for completeness)
            }
            return val + "";
        }
        // Process numeric
        input = input.replaceAll(",", ""); // Remove grouping separators
        if (input.matches("[0-9]+")) { // Direct parse
            return Integer.valueOf(input) + "";
        }
        if (input.matches("[0-9]+(.[0-9]+)?")) {
            return Double.valueOf(input) + "";
        }
        // Process text
        int sum = 0;
        String[] split = input.toLowerCase().split("[ -]");
        // Arrive at final number via summation
        for (String s : split) {
            if (s.equalsIgnoreCase("and")) {
                continue;
            }
            if (s.equalsIgnoreCase("twenty")) { // Annoying special case
                sum += 20;
                continue;
            }
            String prefix = s.substring(0, 3);
            int temp = 0;
            switch (prefix) {
                case "zer":
                    temp += 0;
                    break;
                case "one":
                case "onc":
                    temp += 1;
                    break;
                case "two":
                case "twi":
                    temp += 2;
                    break;
                case "thr":
                case "thi":
                    temp += 3;
                    break;
                case "fou":
                    temp += 4;
                    break;
                case "fiv":
                case "fif":
                    temp += 5;
                    break;
                case "six":
                    temp += 6;
                    break;
                case "sev":
                    temp += 7;
                    break;
                case "eig":
                    temp += 8;
                    break;
                case "nin":
                    temp += 9;
                    break;
                case "ten":
                    temp += 10;
                    break;
                case "ele":
                    temp += 11;
                    break;
                case "twe":
                    temp += 12;
                    break;
            }
            if (s.endsWith("teen")) temp += 10;
            if (s.endsWith("ty")) temp *= 10;
            if (temp != -1) sum += temp;
            if (s.equals("hundred")) {
                if (sum == 0) sum += 100;
                else sum *= 100;
            }
            if (s.equals("thousand")) {
                if (sum == 0) sum += 1000;
                else sum *= 1000;
            }
            if (s.equals("million")) {
                if (sum == 0) sum += 1000000;
                else sum *= 1000000;
            }
        }
        return sum + "";
    }

    public static String getHL7EventTimingCode(int operator, String mainEvent) {
        StringBuilder out = new StringBuilder();
        switch (operator) {
            case -1:
                out.append("A");
                break; //ante
            case 1:
                out.append("P");
                break; //post
            default:
                break;
        }
        switch (mainEvent) {
            // Events
            case "meal":
            case "meals":
                out.append("C");
                break;
            case "breakfast":
                out.append("CM");
                break;
            case "lunch":
                out.append("CD");
                break;
            case "dinner":
                out.append("CV");
                break;
            case "sleep":
            case "bedtime":
                out.append("HS");
                break;
            case "waking":
                out.append("WAKE");
                break;
            // Times of day
            case "morning":
                out.append("MORN");
                break;
            case "afternoon":
                out.append("AFT");
                break;
            case "evening":
                out.append("EVE");
                break;
            case "night":
                out.append("NIGHT");
                break;
            default:
                System.out.println(mainEvent);
                break;
        }
        return out.toString();
    }

    /**
     * Instantiates a FHIR primitive (Must have a "setValue(String) method)
     *
     * @param clazz The class type of the primitive
     * @param cas   The JCAS being manipulated
     * @param value The value of
     * @param <T>   Primitive type
     * @return The instantiated primitive with the value
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    public static <T extends Element> T instantiatePrimitiveWithValue(Class<T> clazz, JCas cas, String value, int begin, int end) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor(JCas.class);
            constructor.setAccessible(true);
            T primitive = constructor.newInstance(cas);
            Method m = clazz.getDeclaredMethod("setValue", String.class);
            m.setAccessible(true);
            m.invoke(primitive, value);
            primitive.setBegin(begin);
            primitive.setEnd(end);
            primitive.addToIndexes();
            return primitive;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | InstantiationException e) {
            throw new IllegalArgumentException("Attempted to instantiate primitive type that was not a primitive");
        }
    }

    public static double convertUCUMToHours(UnitsOfTime unit) {
        switch (unit.getValue().toUpperCase()) {
            case "A":
                return 365 * 24;
            case "MO":
                return 30 * 24;
            case "WK":
                return 7 * 24;
            case "D":
                return 24;
            case "H":
                return 1;
            case "MIN":
                return 1 / 60D;
            case "S":
                return 1 / (60 * 60D);
            default:
                return 0;
        }
    }

    /**
     * Expands boundries to contain a given span
     * @param ann The annotation to expand
     * @param begin start of span
     * @param end end of span
     */
    public static void expand(Annotation ann, int begin, int end) {
        if (ann.getBegin() != 0 || ann.getEnd() != 0) {
            ann.setBegin(Math.min(begin, ann.getBegin()));
            ann.setEnd(Math.max(end, ann.getEnd()));
        } else {
            ann.setBegin(begin);
            ann.setEnd(end);
        }
    }
}
