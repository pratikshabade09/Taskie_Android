package com.taskie.app.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.taskie.app.data.repository.TaskRepository;

/**
 * Re-registers all pending AlarmManager alarms after the device reboots.
 * Alarms don't survive a reboot, so we re-schedule them here.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            TaskRepository repository = new TaskRepository(context);
            repository.rescheduleAllReminders();
        }
    }
}
