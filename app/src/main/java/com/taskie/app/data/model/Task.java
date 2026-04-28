package com.taskie.app.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Task entity – the core data model for Taskie.
 * Stored in the Room database, observed via LiveData throughout the app.
 */
@Entity(tableName = "tasks")
public class Task {

    // ── Priority constants ──────────────────────────────────────────────────
    public static final int PRIORITY_LOW    = 0;
    public static final int PRIORITY_MEDIUM = 1;
    public static final int PRIORITY_HIGH   = 2;

    // ── Tag constants ───────────────────────────────────────────────────────
    public static final String TAG_WORK     = "Work";
    public static final String TAG_STUDY    = "Study";
    public static final String TAG_PERSONAL = "Personal";
    public static final String TAG_HEALTH   = "Health";
    public static final String TAG_FINANCE  = "Finance";
    public static final String TAG_OTHER    = "Other";

    // ── Fields ──────────────────────────────────────────────────────────────
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "description")
    private String description;

    /** Due date/time as Unix epoch milliseconds. 0 means no due date. */
    @ColumnInfo(name = "due_date")
    private long dueDate;

    /** Reminder time as Unix epoch milliseconds. 0 means no reminder. */
    @ColumnInfo(name = "reminder_time")
    private long reminderTime;

    /** PRIORITY_LOW, PRIORITY_MEDIUM, or PRIORITY_HIGH */
    @ColumnInfo(name = "priority")
    private int priority;

    /** Comma-separated list of tag strings, e.g. "Work,Study" */
    @ColumnInfo(name = "tags")
    private String tags;

    @ColumnInfo(name = "is_completed")
    private boolean completed;

    /** Timestamp when the task was completed. 0 if not completed. */
    @ColumnInfo(name = "completed_at")
    private long completedAt;

    /** Creation timestamp – used for "recently completed" ordering. */
    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** Hour-of-day the user typically does tasks with this tag (smart suggestion). */
    @ColumnInfo(name = "suggested_hour")
    private int suggestedHour;

    // ── Constructor ─────────────────────────────────────────────────────────
    public Task(String title, String description, long dueDate, long reminderTime,
                int priority, String tags) {
        this.title       = title;
        this.description = description;
        this.dueDate     = dueDate;
        this.reminderTime = reminderTime;
        this.priority    = priority;
        this.tags        = tags;
        this.completed   = false;
        this.completedAt = 0;
        this.createdAt   = System.currentTimeMillis();
        this.suggestedHour = -1;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public int getId()                   { return id; }
    public void setId(int id)            { this.id = id; }

    public String getTitle()             { return title; }
    public void setTitle(String t)       { this.title = t; }

    public String getDescription()       { return description; }
    public void setDescription(String d) { this.description = d; }

    public long getDueDate()             { return dueDate; }
    public void setDueDate(long d)       { this.dueDate = d; }

    public long getReminderTime()        { return reminderTime; }
    public void setReminderTime(long r)  { this.reminderTime = r; }

    public int getPriority()             { return priority; }
    public void setPriority(int p)       { this.priority = p; }

    public String getTags()              { return tags; }
    public void setTags(String t)        { this.tags = t; }

    public boolean isCompleted()         { return completed; }
    public void setCompleted(boolean c)  { this.completed = c; }

    public long getCompletedAt()         { return completedAt; }
    public void setCompletedAt(long c)   { this.completedAt = c; }

    public long getCreatedAt()           { return createdAt; }
    public void setCreatedAt(long c)     { this.createdAt = c; }

    public int getSuggestedHour()        { return suggestedHour; }
    public void setSuggestedHour(int h)  { this.suggestedHour = h; }

    // ── Helpers ──────────────────────────────────────────────────────────────
    /** Returns a human-readable priority label. */
    public String getPriorityLabel() {
        switch (priority) {
            case PRIORITY_HIGH:   return "High";
            case PRIORITY_MEDIUM: return "Medium";
            default:              return "Low";
        }
    }

    /** True if the task has a due date and it is in the past (and not completed). */
    public boolean isOverdue() {
        return !completed && dueDate > 0 && dueDate < System.currentTimeMillis();
    }

    /** True if due date falls on today's calendar date. */
    public boolean isDueToday() {
        if (dueDate == 0) return false;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(dueDate);
        java.util.Calendar today = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.YEAR)  == today.get(java.util.Calendar.YEAR)
            && cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR);
    }
}
