package com.taskie.app.voice;

import com.taskie.app.data.model.Task;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based intent parser.
 * Converts free-form voice input into a structured Task object.
 *
 * Supported patterns:
 *   "Call John tomorrow at 6 PM"
 *   "Buy groceries on Friday"
 *   "Submit report by 3 PM today"
 *   "Remind me to exercise every morning"
 *   "Urgent meeting with team next Monday at 10 AM"
 *   "Delete task X"       → returns null (caller handles delete intent)
 *   "Mark X as done"      → returns null (caller handles done intent)
 */
public class IntentParser {

    // ── Intent types ──────────────────────────────────────────────────────────
    public static final String INTENT_ADD    = "ADD";
    public static final String INTENT_DELETE = "DELETE";
    public static final String INTENT_DONE   = "DONE";
    public static final String INTENT_UNKNOWN = "UNKNOWN";

    // ── Regex patterns ────────────────────────────────────────────────────────

    // Time: "at 3 PM", "at 10:30 AM", "at 14:00"
    private static final Pattern TIME_PATTERN =
        Pattern.compile("\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM|am|pm)?", Pattern.CASE_INSENSITIVE);

    // Relative day keywords
    private static final Pattern TODAY_PATTERN    = Pattern.compile("\\btoday\\b",    Pattern.CASE_INSENSITIVE);
    private static final Pattern TOMORROW_PATTERN = Pattern.compile("\\btomorrow\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_WEEK_PATTERN= Pattern.compile("\\bnext\\s+week\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern THIS_WEEKEND_PATTERN= Pattern.compile("\\b(this\\s+)?weekend\\b", Pattern.CASE_INSENSITIVE);

    // Named day of week
    private static final Pattern DAY_OF_WEEK_PATTERN =
        Pattern.compile("\\b(on\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
                        "next\\s+monday|next\\s+tuesday|next\\s+wednesday|next\\s+thursday|" +
                        "next\\s+friday|next\\s+saturday|next\\s+sunday)\\b", Pattern.CASE_INSENSITIVE);

    // "by" date/time
    private static final Pattern BY_PATTERN =
        Pattern.compile("\\bby\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM|am|pm)?", Pattern.CASE_INSENSITIVE);

    // Delete intent
    private static final Pattern DELETE_PATTERN =
        Pattern.compile("^(delete|remove|cancel)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    // Done intent
    private static final Pattern DONE_PATTERN =
        Pattern.compile("^(mark|complete|finish|done with)\\s+(.+?)\\s+(as\\s+)?(done|complete|finished)?$",
                        Pattern.CASE_INSENSITIVE);

    // Priority keywords
    private static final String[] HIGH_KEYWORDS   = { "urgent", "asap", "immediately", "critical", "important", "emergency" };
    private static final String[] LOW_KEYWORDS    = { "whenever", "someday", "maybe", "eventually", "low priority" };

    // Tag keywords
    private static final Map<String, String> TAG_KEYWORDS = new HashMap<String, String>() {{
        put("meeting",   Task.TAG_WORK);
        put("work",      Task.TAG_WORK);
        put("office",    Task.TAG_WORK);
        put("report",    Task.TAG_WORK);
        put("project",   Task.TAG_WORK);
        put("study",     Task.TAG_STUDY);
        put("homework",  Task.TAG_STUDY);
        put("exam",      Task.TAG_STUDY);
        put("class",     Task.TAG_STUDY);
        put("lecture",   Task.TAG_STUDY);
        put("gym",       Task.TAG_HEALTH);
        put("exercise",  Task.TAG_HEALTH);
        put("workout",   Task.TAG_HEALTH);
        put("doctor",    Task.TAG_HEALTH);
        put("medicine",  Task.TAG_HEALTH);
        put("groceries", Task.TAG_PERSONAL);
        put("shopping",  Task.TAG_PERSONAL);
        put("family",    Task.TAG_PERSONAL);
        put("pay",       Task.TAG_FINANCE);
        put("bank",      Task.TAG_FINANCE);
        put("bill",      Task.TAG_FINANCE);
        put("invoice",   Task.TAG_FINANCE);
    }};

    // "Remind me to" / "Remember to" strippers
    private static final Pattern REMINDER_PREFIX =
        Pattern.compile("^(remind\\s+me\\s+to|remember\\s+to|don'?t\\s+forget\\s+to|i\\s+need\\s+to|" +
                        "i\\s+have\\s+to|i\\s+must|make\\s+sure\\s+to)\\s+", Pattern.CASE_INSENSITIVE);

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Parses a voice command string into a Task.
     * Returns null if the command is not an add-task intent.
     */
    public static Task parse(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String text = input.trim();

        // Detect delete intent → return null (the caller should handle)
        if (DELETE_PATTERN.matcher(text).find()) return null;
        if (DONE_PATTERN.matcher(text).find())   return null;

        // Strip reminder prefixes
        text = REMINDER_PREFIX.matcher(text).replaceFirst("");

        // Extract date/time (do this before stripping for the title)
        long dueDate     = extractDueDate(text);
        long reminderTime = dueDate > 0 ? dueDate - 15 * 60 * 1000 : 0; // 15 min before

        // Extract clean task title
        String title = extractTitle(text);
        if (title.isEmpty()) return null;

        // Capitalise first letter
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);

        // Detect priority
        int priority = detectPriority(text);

        // Detect tags
        String tags = detectTags(text);

        return new Task(title, "", dueDate, reminderTime, priority, tags);
    }

    /** Returns the detected intent type without building a Task. */
    public static String detectIntent(String input) {
        if (input == null) return INTENT_UNKNOWN;
        if (DELETE_PATTERN.matcher(input).find()) return INTENT_DELETE;
        if (DONE_PATTERN.matcher(input).find())   return INTENT_DONE;
        return INTENT_ADD;
    }

    /** For DONE intent, extracts the task title to mark done. */
    public static String extractDoneTitle(String input) {
        Matcher m = DONE_PATTERN.matcher(input);
        if (m.find()) return m.group(2);
        return null;
    }

    /** For DELETE intent, extracts the task title to delete. */
    public static String extractDeleteTitle(String input) {
        Matcher m = DELETE_PATTERN.matcher(input);
        if (m.find()) return m.group(2);
        return null;
    }

    // ── Date extraction ───────────────────────────────────────────────────────

    private static long extractDueDate(String text) {
        Calendar cal = Calendar.getInstance();

        // Parse hour/minute from time patterns
        int hour   = -1;
        int minute = 0;

        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        boolean found = timeMatcher.find();

        if (!found) {
            timeMatcher = BY_PATTERN.matcher(text);
            found = timeMatcher.find();
        }

        if (found && timeMatcher.group(1) != null) {
            try {
                hour = Integer.parseInt(timeMatcher.group(1));
                minute = timeMatcher.group(2) != null ? Integer.parseInt(timeMatcher.group(2)) : 0;

                String ampm = timeMatcher.group(3);
                if (ampm != null) {
                    if (ampm.equalsIgnoreCase("PM") && hour < 12) hour += 12;
                    if (ampm.equalsIgnoreCase("AM") && hour == 12) hour = 0;
                } else {
                    if (hour > 0 && hour <= 7) hour += 12;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
//        if (timeMatcher.groupCount() >= 1 && timeMatcher.group(1) != null) {
//            try {
//                hour   = Integer.parseInt(timeMatcher.group(1));
//                minute = timeMatcher.group(2) != null ? Integer.parseInt(timeMatcher.group(2)) : 0;
//                String ampm = timeMatcher.group(3);
//                if (ampm != null) {
//                    if (ampm.equalsIgnoreCase("PM") && hour < 12) hour += 12;
//                    if (ampm.equalsIgnoreCase("AM") && hour == 12) hour = 0;
//                } else {
//                    // Heuristic: if hour <= 7 and no AM/PM, assume PM
//                    if (hour > 0 && hour <= 7) hour += 12;
//                }
//            }
//            catch (NumberFormatException ignored) {}
//        }

        // Determine the day
        if (TODAY_PATTERN.matcher(text).find()) {
            // use today
        } else if (TOMORROW_PATTERN.matcher(text).find()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        } else if (NEXT_WEEK_PATTERN.matcher(text).find()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        } else if (THIS_WEEKEND_PATTERN.matcher(text).find()) {
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            int daysToSat = Calendar.SATURDAY - dow;
            if (daysToSat <= 0) daysToSat += 7;
            cal.add(Calendar.DAY_OF_YEAR, daysToSat);
        } else {
            Matcher dayMatcher = DAY_OF_WEEK_PATTERN.matcher(text);
            if (dayMatcher.find()) {
                String dayStr = dayMatcher.group(2).toLowerCase().replaceFirst("next\\s+", "");
                boolean isNext = dayMatcher.group(2).toLowerCase().startsWith("next");
                int targetDow = dayNameToDow(dayStr);
                if (targetDow > 0) {
                    int currentDow = cal.get(Calendar.DAY_OF_WEEK);
                    int diff = targetDow - currentDow;
                    if (diff <= 0 || isNext) diff += 7;
                    cal.add(Calendar.DAY_OF_YEAR, diff);
                }
            } else if (hour < 0) {
                // No date/time info found at all
                return 0;
            }
            // Else: no day keyword → assume today
        }

        // Apply time
        if (hour >= 0) {
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        } else {
            // Default: end of day
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 0);
        }

        return cal.getTimeInMillis();
    }

    private static int dayNameToDow(String name) {
        switch (name.trim().toLowerCase()) {
            case "monday":    return Calendar.MONDAY;
            case "tuesday":   return Calendar.TUESDAY;
            case "wednesday": return Calendar.WEDNESDAY;
            case "thursday":  return Calendar.THURSDAY;
            case "friday":    return Calendar.FRIDAY;
            case "saturday":  return Calendar.SATURDAY;
            case "sunday":    return Calendar.SUNDAY;
            default:          return -1;
        }
    }

    // ── Title extraction ──────────────────────────────────────────────────────

    private static String extractTitle(String text) {
        // Remove time phrases
        String cleaned = TIME_PATTERN.matcher(text).replaceAll("");
        cleaned = BY_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TODAY_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TOMORROW_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = NEXT_WEEK_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = THIS_WEEKEND_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = DAY_OF_WEEK_PATTERN.matcher(cleaned).replaceAll("");

        // Remove priority keywords
        for (String kw : HIGH_KEYWORDS) {
            cleaned = cleaned.replaceAll("(?i)\\b" + kw + "\\b", "");
        }

        // Clean up whitespace and trailing prepositions
        cleaned = cleaned.replaceAll("\\s+(on|at|by|in|for|to|the)\\s*$", "");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();

        return cleaned;
    }

    // ── Priority detection ────────────────────────────────────────────────────

    private static int detectPriority(String text) {
        String lower = text.toLowerCase();
        for (String kw : HIGH_KEYWORDS) {
            if (lower.contains(kw)) return Task.PRIORITY_HIGH;
        }
        for (String kw : LOW_KEYWORDS) {
            if (lower.contains(kw)) return Task.PRIORITY_LOW;
        }
        return Task.PRIORITY_MEDIUM;
    }

    // ── Tag detection ─────────────────────────────────────────────────────────

    private static String detectTags(String text) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : TAG_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return Task.TAG_PERSONAL;
    }
}
