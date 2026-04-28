package com.taskie.app.notification;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.taskie.app.R;
import com.taskie.app.data.model.Task;
import com.taskie.app.ui.main.MainActivity;

/**
 * Central notification utility.
 * Handles channel creation, scheduling alarms via AlarmManager,
 * showing notifications with action buttons (Done · Snooze · Reschedule),
 * and the daily summary notification.
 */
public class NotificationHelper {

    // ── Channel IDs ───────────────────────────────────────────────────────────
    public static final String CHANNEL_REMINDERS = "taskie_reminders";
    public static final String CHANNEL_OVERDUE   = "taskie_overdue";
    public static final String CHANNEL_SUMMARY   = "taskie_summary";

    // ── Notification IDs ──────────────────────────────────────────────────────
    public static final int NOTIF_SUMMARY = 9000;

    // ── Intent actions ────────────────────────────────────────────────────────
    public static final String ACTION_TASK_DONE       = "com.taskie.app.ACTION_TASK_DONE";
    public static final String ACTION_TASK_SNOOZE     = "com.taskie.app.ACTION_TASK_SNOOZE";
    public static final String ACTION_TASK_RESCHEDULE = "com.taskie.app.ACTION_TASK_RESCHEDULE";
    public static final String ACTION_TRIGGER_ALARM   = "com.taskie.app.ACTION_TRIGGER_ALARM";

    public static final String EXTRA_TASK_ID    = "extra_task_id";
    public static final String EXTRA_TASK_TITLE = "extra_task_title";

    private final Context context;
    private final AlarmManager alarmManager;

    public NotificationHelper(Context context) {
        this.context      = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    // ── Channel registration (call once at app start) ─────────────────────────

    public static void createChannels(Context ctx) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        // Task reminders channel
        NotificationChannel reminders = new NotificationChannel(
                CHANNEL_REMINDERS,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription("Reminders for your upcoming tasks");
        reminders.enableVibration(true);
        reminders.enableLights(true);
        reminders.setLightColor(Color.parseColor("#14B8A6"));
        nm.createNotificationChannel(reminders);

        // Overdue alerts channel
        NotificationChannel overdue = new NotificationChannel(
                CHANNEL_OVERDUE,
                "Overdue Alerts",
                NotificationManager.IMPORTANCE_DEFAULT);
        overdue.setDescription("Alerts for tasks that are past due");
        nm.createNotificationChannel(overdue);

        // Daily summary channel
        NotificationChannel summary = new NotificationChannel(
                CHANNEL_SUMMARY,
                "Daily Summary",
                NotificationManager.IMPORTANCE_LOW);
        summary.setDescription("Daily overview of your tasks");
        nm.createNotificationChannel(summary);
    }

    // ── Schedule reminder ─────────────────────────────────────────────────────

    /**
     * Schedules an exact alarm that fires at task.getReminderTime().
     * The alarm triggers NotificationReceiver which then shows the notification.
     */
    public void scheduleReminder(Task task) {
        long triggerAt = task.getReminderTime();
        if (triggerAt <= System.currentTimeMillis()) return;

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(ACTION_TRIGGER_ALARM);
        intent.putExtra(EXTRA_TASK_ID,    task.getId());
        intent.putExtra(EXTRA_TASK_TITLE, task.getTitle());

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                task.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } catch (SecurityException e) {
                // Fallback for devices without SCHEDULE_EXACT_ALARM permission granted
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
    }

    /** Cancels a previously scheduled reminder. */
    public void cancelReminder(int taskId) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(ACTION_TRIGGER_ALARM);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, taskId, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null && alarmManager != null) {
            alarmManager.cancel(pi);
            pi.cancel();
        }
        // Also dismiss the notification if it's already showing
        NotificationManagerCompat.from(context).cancel(taskId);
    }

    // ── Show task reminder notification ───────────────────────────────────────

    public void showTaskReminder(int taskId, String taskTitle) {
        // Tap → open MainActivity
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                context, taskId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action: Mark Done
        PendingIntent donePi  = buildActionPendingIntent(ACTION_TASK_DONE,      taskId, taskTitle);
        // Action: Snooze 30 min
        PendingIntent snoozePi = buildActionPendingIntent(ACTION_TASK_SNOOZE,   taskId, taskTitle);
        // Action: Reschedule
        PendingIntent reschedPi = buildActionPendingIntent(ACTION_TASK_RESCHEDULE, taskId, taskTitle);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Task Reminder")
                .setContentText(taskTitle)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(taskTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#14B8A6"))
                .setVibrate(new long[]{0, 250, 100, 250})
                .addAction(R.drawable.ic_check_circle, "Done",      donePi)
                .addAction(R.drawable.ic_snooze,       "Snooze 30m", snoozePi)
                .addAction(R.drawable.ic_reschedule,   "Reschedule", reschedPi);

        try {
            NotificationManagerCompat.from(context).notify(taskId, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted on API 33+ (handled at runtime in UI)
        }
    }

    // ── Daily summary notification ────────────────────────────────────────────

    public void showDailySummary(int activeCount, int completedYesterday) {
        String body;
        if (activeCount == 0) {
            body = "No tasks today. Enjoy your day! 🎉";
        } else {
            body = "You have " + activeCount + " task" + (activeCount > 1 ? "s" : "") + " today.";
            if (completedYesterday > 0) {
                body += " You completed " + completedYesterday + " yesterday — great work!";
            }
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                context, NOTIF_SUMMARY, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SUMMARY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Good morning! ☀️")
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#14B8A6"));

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_SUMMARY, builder.build());
        } catch (SecurityException ignored) {}
    }

    // ── Missed task alert ─────────────────────────────────────────────────────

    public void showOverdueAlert(int taskId, String taskTitle) {
        PendingIntent donePi = buildActionPendingIntent(ACTION_TASK_DONE, taskId, taskTitle);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_OVERDUE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Missed task ⚠️")
                .setContentText(taskTitle + " is overdue")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#EF4444"))
                .addAction(R.drawable.ic_check_circle, "Mark Done", donePi);

        try {
            NotificationManagerCompat.from(context).notify(taskId + 10000, builder.build());
        } catch (SecurityException ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PendingIntent buildActionPendingIntent(String action, int taskId, String taskTitle) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_TASK_ID,    taskId);
        intent.putExtra(EXTRA_TASK_TITLE, taskTitle);
        // Use unique requestCode per action+task so PendingIntents don't collide
        int reqCode = (action + taskId).hashCode();
        return PendingIntent.getBroadcast(context, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
