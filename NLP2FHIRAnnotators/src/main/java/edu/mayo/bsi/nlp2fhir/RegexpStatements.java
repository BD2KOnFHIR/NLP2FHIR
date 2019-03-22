package edu.mayo.bsi.nlp2fhir;

import java.util.regex.Pattern;

/**
 * A class that contains static references to all regular expressions used in this application
 */
public class RegexpStatements {

    /**
     * A regular expression for parsing frequency and period strings. <br>
     * Should be used with CASE_INSENSITIVE and MULTILINE flags <br>
     * Important match groups (all are optional):<br>
     * - 1: frequency count <br>
     * - 2: frequency count 2 (if range in the form "x-y times") <br>
     * - 3: special -lys e.g. once, twice, thrice
     * - 4: will always contain "every" if present: indicates that frequency should be assumed to be 1. Should be checked for the word "other" indicating period should be multiplied by 2<br>
     * - 5: separator contains the word "other" (e.g. "every other day") multiply period by 2
     * - 7: period length <br>
     * - 8: period range indicator (if not set, period length is equal to match 5 + match 7)
     * - 9: period length 2 (if range in the form "x-y hours/days/etc), otherwise append to match 5 for period length" <br>
     * - 10: time period unit (e.g. hours, days, seconds, etc) <br>
     * - 11: special -lys that can be seen as a frequency even while standalone
     */
    public static Pattern FREQPERIOD = Pattern.compile(
            // Header Numeric or numeric range or the word "every"
            "(?:(?:((?:one|two|three|four|five|six|seven|eight|nine|ten|[.0-9]+))" +
                    "(?:-((?:(?:one|two|three|four|five|six|seven|eight|nine|ten|[.0-9]+))))? times?" +
                    "|(?:(once|twice|thrice) ?-? ?)|[.0-9]|(every(?:[ -]other)?))" +
                    // Separator
                    "(?:[ -]a[ -]| ?\\/ ?|[ -]every[ -](other[ -])?|[ -]per[ -]|[ -]|)" +
                    // Period length or length range
                    "((?:(?:(one|two|three|four|five|six|seven|eight|nine|ten|[.0-9]+)" +
                    "?(-)?(one|two|three|four|five|six|seven|eight|nine|ten|[.0-9]+) ?)?" +
                    "(hourly|daily|monthly|weekly|yearly|day|hour|week|month|year|second|minute|monday|tuesday|wednesday|thursday|friday|saturday|sunday|d\\b|m\\b|y\\b|s\\b|h\\b|w\\b))s?))" +
                    "|(hourly|daily|monthly|weekly|yearly)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /**
     * A regular expression for parsing special case frequencies involving days of the week,
     * such as "on mondays, tuesdays, and wednesdays". Due to unreliability of imported time annotations, we run this on the whole
     * sentence involved, not just on a given chunk.<br>
     * <br>
     * Group 1: present if "every other" is the header (period = 2 weeks) <br>
     * Group 2: comma, space, or dash separated list of days <br>
     */
    public static Pattern PERIOD_WEEKDAYS = Pattern.compile("(?:on|every (other)?)((?:[, -]+?(?:and )?[montuewdhfrisa]{3,6}days?)+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /**
     * Parses out days of the week, group 1 is always present and contains the first three letters of the weekday
     */
    public static Pattern WEEKDAY_PARSER = Pattern.compile("([montuewdhfrisa]{3,6})days?", Pattern.CASE_INSENSITIVE);

    /**
     * A regular expression for identifying statements denoting events, such as before breakfast, at lunch, after sleep, etcetc<br>
     * <br>
     * Group 1: before, after, at, during, every, with
     * Group 2: breakfast, lunch, dinner, meal(s)
     */
    public static Pattern TIMING_EVENTS = Pattern.compile("(before|after|at|during|every|with|on)(?: )+(breakfast|lunch|dinner|meals?|sleep|bedtime|waking)");

    /**
     * A regular expression that identifies times of day (morning, afternoon, evening, night)
     */
    public static Pattern TIME_OF_DAY = Pattern.compile("(?:at|in the|every|during the)(?: )+(morning|afternoon|evening|night)");

    /**
     * A regular expression that identifies current as of {date} statements
     */
    public static Pattern CURRENT_AS_OF = Pattern.compile("(?:are the (?:[\\w|\\s]+) as of|evaluated on)", Pattern.CASE_INSENSITIVE);

}
