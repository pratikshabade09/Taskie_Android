package com.taskie.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Central date/time utility.
 * All formatting and range helpers live here so formatting is consistent everywhere.
 */
public final class DateUtils {

    private static final SimpleDateFormat FMT_DATE_TIME =
            new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault());
    private static final SimpleDateFormat FMT_DATE_ONLY =
            new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
    private static final SimpleDateFormat FMT_TIME_ONLY =
            new SimpleDateFormat("h:mm a", Locale.getDefault());
    private static final SimpleDateFormat FMT_DAY_LABEL =
            new SimpleDateFormat("EEEE", Locale.getDefault());

    private DateUtils() {}

    // ── Formatting ────────────────────────────────────────────────────────────

    /** Full date + time: "Mon, Jun 3 · 6:00 PM" */
    public static String formatDateTime(long ms) {
        return FMT_DATE_TIME.format(new Date(ms));
    }

    /** Date only: "Mon, Jun 3" */
    public static String formatDate(long ms) {
        return FMT_DATE_ONLY.format(new Date(ms));
    }

    /** Time only: "6:00 PM" */
    public static String formatTime(long ms) {
        return FMT_TIME_ONLY.format(new Date(ms));
    }

    /**
     * Relative label: "Today · 6 PM", "Tomorrow · 10 AM", "Wed, Jun 5 · 3 PM"
     * Used in task cards for due date display.
     */
    public static String formatRelative(long ms) {
        if (ms <= 0) return "";
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(ms);
        Calendar now = Calendar.getInstance();

        boolean sameYear = target.get(Calendar.YEAR) == now.get(Calendar.YEAR);
        int dayDiff = target.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR);
        if (!sameYear) dayDiff = (target.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 365;

        String timeStr = formatTime(ms);
        if (dayDiff == 0)      return "Today · " + timeStr;
        if (dayDiff == 1)      return "Tomorrow · " + timeStr;
        if (dayDiff == -1)     return "Yesterday · " + timeStr;
        if (dayDiff < -1)      return Math.abs(dayDiff) + " days overdue";
        if (dayDiff <= 7)      return FMT_DAY_LABEL.format(new Date(ms)) + " · " + timeStr;
        return formatDateTime(ms);
    }

    /**
     * Voice-friendly date description: "tomorrow at 6 PM", "on Friday at 3 PM"
     */
    public static String formatVoice(long ms) {
        if (ms <= 0) return "";
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(ms);
        Calendar now = Calendar.getInstance();

        int dayDiff = target.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR);
        String timeStr = formatTime(ms).toLowerCase(Locale.getDefault());

        if (dayDiff == 0) return "today at " + timeStr;
        if (dayDiff == 1) return "tomorrow at " + timeStr;
        return "on " + FMT_DAY_LABEL.format(new Date(ms)) + " at " + timeStr;
    }

    // ── Range helpers ─────────────────────────────────────────────────────────

    /** Returns [startOfToday, startOfTomorrow] in milliseconds. */
    public static long[] getTodayRange() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);

        return new long[]{ start.getTimeInMillis(), end.getTimeInMillis() };
    }

    /** Returns [startOfYesterday, startOfToday] in milliseconds. */
    public static long[] getYesterdayRange() {
        long[] today = getTodayRange();
        return new long[]{ today[0] - 86_400_000L, today[0] };
    }

    /** Returns the start-of-day timestamp for a given epoch ms. */
    public static long startOfDay(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Returns a short day label for a chart x-axis: "Mon", "Tue", etc. */
    public static String shortDayLabel(long ms) {
        SimpleDateFormat fmt = new SimpleDateFormat("EEE", Locale.getDefault());
        return fmt.format(new Date(ms));
    }

    /** True if the two epoch ms values fall on the same calendar day. */
    public static boolean isSameDay(long ms1, long ms2) {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTimeInMillis(ms1);
        c2.setTimeInMillis(ms2);
        return c1.get(Calendar.YEAR)        == c2.get(Calendar.YEAR)
            && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }
}
