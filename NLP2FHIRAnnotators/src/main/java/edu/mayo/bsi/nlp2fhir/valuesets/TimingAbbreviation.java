package edu.mayo.bsi.nlp2fhir.valuesets;

import java.util.HashMap;
import java.util.Map;

/**
 * TimingAbbreviation value set defined at http://hl7.org/fhir/ValueSet/timing-abbreviation
 */
public enum TimingAbbreviation {
    BID(2,1,24),
    TID(3,1,24),
    QID(4,1,24),
    AM(-1,1,24), //TODO implementation for AM/PM
    PM(-1,1,24),
    QD(1,1,24),
    QOD(1,2,24),
    Q4H(1,1,4),
    Q6H(1,1,6);
    private static Map<Double, Map<Double, Map<Double, TimingAbbreviation>>> LOOKUP;

    private double freq;
    private double everyXDays;
    private double periodHrs;

    static {
        LOOKUP = new HashMap<>();
        for (TimingAbbreviation c : values()) {
            LOOKUP.merge(c.freq, new HashMap<>(), (v_curr,v_new) -> v_curr)
                    .merge(c.everyXDays, new HashMap<>(), (v_curr, v_new) -> v_curr)
                    .put(c.periodHrs, c);
        }
    }

    TimingAbbreviation(double freq, double everyXDays, double periodHrs) {
        this.freq = freq;
        this.everyXDays = everyXDays;
        this.periodHrs = periodHrs;
    }

    public static TimingAbbreviation getByTiming(double freq, double period, double periodHrs) {
        return LOOKUP.getOrDefault(freq, new HashMap<>()).getOrDefault(period, new HashMap<>()).get(periodHrs);
    }
}
