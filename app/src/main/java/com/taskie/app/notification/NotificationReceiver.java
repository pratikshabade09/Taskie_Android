package com.taskie.app.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.taskie.app.data.database.TaskDatabase;
import com.taskie.app.data.model.Task;
import com.taskie.app.ui.addedittask.AddEditTaskActivity;

import java.util.concurrent.Executors;

/**
 * Handles notification action buttons and alarm trigger broadcasts.
 *
 * Actions:
 *   ACTION_TRIGGER_ALARM   → show the reminder notification
 *   ACTION_TASK_DONE       → mark task complete + dismiss notification
 *   ACTION_TASK_SNOOZE     → reschedule reminder +30 minutes
 *   ACTION_TASK_RESCHEDULE → Open AddEditTaskActivity for that task
 */
public class NotificationReceiver extends BroadcastReceiver {

    private static final long SNOOZE_DURATION_MS  = 30 * 60 * 1000L;  // 30 min

    @Override
    public void onReceive(Context context, Intent intent) {
        String action    = intent.getAction();
        int    taskId    = intent.getIntExtra(NotificationHelper.EXTRA_TASK_ID,    -1);
        String taskTitle = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE);

        if (action == null || taskId < 0) return;

        NotificationHelper helper = new NotificationHelper(context);

        switch (action) {
            case NotificationHelper.ACTION_TRIGGER_ALARM:
                // Alarm fired → show the rich notification
                helper.showTaskReminder(taskId, taskTitle != null ? taskTitle : "Task reminder");
                break;

            case NotificationHelper.ACTION_TASK_DONE:
                // Dismiss notification and mark the task complete in DB
                NotificationManagerCompat.from(context).cancel(taskId);
                markTaskComplete(context, taskId);
                break;

            case NotificationHelper.ACTION_TASK_SNOOZE:
                // Dismiss current notification, reschedule +30 min
                NotificationManagerCompat.from(context).cancel(taskId);
                snoozeTask(context, helper, taskId, taskTitle, SNOOZE_DURATION_MS);
                break;

            case NotificationHelper.ACTION_TASK_RESCHEDULE:
                // Dismiss current notification, open AddEditTaskActivity
                NotificationManagerCompat.from(context).cancel(taskId);
                openReschedule(context, taskId);
                break;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void markTaskComplete(Context context, int taskId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            TaskDatabase db   = TaskDatabase.getInstance(context);
            Task task         = db.taskDao().getById(taskId);
            if (task != null) {
                task.setCompleted(true);
                task.setCompletedAt(System.currentTimeMillis());
                db.taskDao().update(task);
            }
        });
    }

    private void snoozeTask(Context context, NotificationHelper helper,
                            int taskId, String taskTitle, long delayMs) {
        long newReminderTime = System.currentTimeMillis() + delayMs;

        Executors.newSingleThreadExecutor().execute(() -> {
            TaskDatabase db = TaskDatabase.getInstance(context);
            Task task       = db.taskDao().getById(taskId);
            if (task != null) {
                task.setReminderTime(newReminderTime);
                db.taskDao().update(task);
                helper.scheduleReminder(task);
            } else {
                // Task not found (maybe deleted); fire a raw alarm anyway
                Task temp = new Task(taskTitle != null ? taskTitle : "Task", "", 0,
                                     newReminderTime, Task.PRIORITY_MEDIUM, "");
                temp.setId(taskId);
                helper.scheduleReminder(temp);
            }
        });
    }

    private void openReschedule(Context context, int taskId) {
        Intent intent = new Intent(context, AddEditTaskActivity.class);
        intent.putExtra(AddEditTaskActivity.EXTRA_TASK_ID, taskId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);

        // Close notification drawer (optional but better UX)
        Intent closeIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(closeIntent);
    }
}
