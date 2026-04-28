package com.taskie.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.taskie.app.data.model.Task;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight intelligence layer — no ML required.
 * Detects user patterns stored in SharedPreferences and applies them
 * to surface smart suggestions when creating tasks.
 *
 * Features:
 *  1. Auto-detect priority from title keywords
 *  2. Suggest best time-of-day per tag (learned from past completions)
 *  3. Pattern phrases ("You usually study at night")
 */
public class SmartSuggestionEngine {

    private static final String PREFS_NAME = "taskie_smart_prefs";

    // Keys: "tag_hoursum_Work", "tag_hourcount_Work" → average hour = sum/count
    private static final String KEY_HOUR_SUM   = "tag_hoursum_";
    private static final String KEY_HOUR_COUNT = "tag_hourcount_";

    // Priority keyword map (title words → priority)
    private static final Map<String, Integer> PRIORITY_KEYWORDS = new HashMap<String, Integer>() {{
        put("urgent",      Task.PRIORITY_HIGH);
        put("asap",        Task.PRIORITY_HIGH);
        put("critical",    Task.PRIORITY_HIGH);
        put("important",   Task.PRIORITY_HIGH);
        put("emergency",   Task.PRIORITY_HIGH);
        put("deadline",    Task.PRIORITY_HIGH);
        put("immediately", Task.PRIORITY_HIGH);
        put("must",        Task.PRIORITY_HIGH);
        put("final",       Task.PRIORITY_HIGH);
        put("submit",      Task.PRIORITY_MEDIUM);
        put("meeting",     Task.PRIORITY_MEDIUM);
        put("call",        Task.PRIORITY_MEDIUM);
        put("review",      Task.PRIORITY_MEDIUM);
        put("check",       Task.PRIORITY_LOW);
        put("maybe",       Task.PRIORITY_LOW);
        put("whenever",    Task.PRIORITY_LOW);
        put("someday",     Task.PRIORITY_LOW);
    }};

    // Time-of-day labels
    private static final String[] TIME_LABELS = {
        "midnight", "early morning", "early morning", "early morning",
        "early morning", "early morning", "morning", "morning",
        "morning", "morning", "mid-morning", "mid-morning",
        "noon", "afternoon", "afternoon", "afternoon",
        "afternoon", "evening", "evening", "evening",
        "night", "night", "night", "late night"
    };

    private final SharedPreferences prefs;

    public SmartSuggestionEngine(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Priority detection ────────────────────────────────────────────────────

    /**
     * Scans task title words against priority keyword map.
     * Returns the highest matched priority, or MEDIUM as default.
     */
    public int detectPriorityFromTitle(String title) {
        if (title == null || title.isEmpty()) return Task.PRIORITY_MEDIUM;
        String lower = title.toLowerCase();
        int maxPriority = Task.PRIORITY_LOW;
        boolean matched = false;

        for (Map.Entry<String, Integer> entry : PRIORITY_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                if (entry.getValue() > maxPriority) {
                    maxPriority = entry.getValue();
                }
                matched = true;
            }
        }
        return matched ? maxPriority : Task.PRIORITY_MEDIUM;
    }

    // ── Time suggestion ───────────────────────────────────────────────────────

    /**
     * Records the completion hour for a given tag.
     * Call this when a task is marked complete.
     */
    public void recordCompletion(String tag, long completedAtMs) {
        if (tag == null || tag.isEmpty()) return;
        for (String t : tag.split(",")) {
            String trimmed = t.trim();
            if (trimmed.isEmpty()) continue;

            int hour = getHourOfDay(completedAtMs);
            SharedPreferences.Editor editor = prefs.edit();
            long sum   = prefs.getLong(KEY_HOUR_SUM   + trimmed, 0) + hour;
            long count = prefs.getLong(KEY_HOUR_COUNT + trimmed, 0) + 1;
            editor.putLong(KEY_HOUR_SUM   + trimmed, sum);
            editor.putLong(KEY_HOUR_COUNT + trimmed, count);
            editor.apply();
        }
    }

    /**
     * Returns the suggested hour-of-day for a given tag based on past completions.
     * Returns -1 if insufficient data (< 3 completions).
     */
    public int getSuggestedHour(String tag) {
        if (tag == null || tag.isEmpty()) return -1;
        long sum   = prefs.getLong(KEY_HOUR_SUM   + tag, 0);
        long count = prefs.getLong(KEY_HOUR_COUNT + tag, 0);
        if (count < 3) return -1;
        return (int)(sum / count);
    }

    /**
     * Returns a human-readable suggestion string for a tag.
     * Example: "You usually do Work tasks in the evening"
     */
    public String getSuggestionLabel(String tag) {
        int hour = getSuggestedHour(tag);
        if (hour < 0) return null;
        String timeLabel = TIME_LABELS[Math.min(hour, TIME_LABELS.length - 1)];
        return "You usually do " + tag + " tasks in the " + timeLabel;
    }

    /**
     * Returns the best Calendar reminder time for a tag.
     * Defaults to "tonight at 8 PM" if no pattern exists.
     */
    public long getSuggestedReminderTime(String tag) {
        int hour = getSuggestedHour(tag);
        if (hour < 0) hour = 20; // Default: 8 PM

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If that time is in the past today, push to tomorrow
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    // ── Pattern phrases ───────────────────────────────────────────────────────

    /**
     * Returns a list of insight phrases about user patterns.
     * Example: ["You usually study at night", "Most Work tasks are high priority"]
     */
    public java.util.List<String> getInsights() {
        java.util.List<String> insights = new java.util.ArrayList<>();
        String[] tags = { Task.TAG_WORK, Task.TAG_STUDY, Task.TAG_PERSONAL,
                          Task.TAG_HEALTH, Task.TAG_FINANCE };
        for (String tag : tags) {
            String insight = getSuggestionLabel(tag);
            if (insight != null) insights.add(insight);
        }
        return insights;
    }

    // ── Streak calculation ────────────────────────────────────────────────────

    /**
     * Returns how many consecutive days the user has completed at least one task.
     * Reads from the separate streak pref keys.
     */
    public int getCurrentStreak() {
        return prefs.getInt("current_streak", 0);
    }

    /** Call once per day when at least one task is completed. */
    public void updateStreak() {
        long lastStreakDay = prefs.getLong("last_streak_day", 0);
        long[] yesterday   = DateUtils.getYesterdayRange();
        long[] today       = DateUtils.getTodayRange();
        int streak         = prefs.getInt("current_streak", 0);

        if (lastStreakDay >= yesterday[0] && lastStreakDay < today[1]) {
            // Last completion was yesterday or today → continue streak
            streak++;
        } else if (lastStreakDay < yesterday[0]) {
            // Gap → reset streak
            streak = 1;
        }
        prefs.edit()
             .putInt("current_streak", streak)
             .putLong("last_streak_day", System.currentTimeMillis())
             .apply();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getHourOfDay(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(Calendar.HOUR_OF_DAY);
    }
}
