package com.taskie.app;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.taskie.app.notification.DailySummaryWorker;
import com.taskie.app.notification.NotificationHelper;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Application entry point.
 * Initialises notification channels and schedules the daily summary worker.
 */
public class TaskieApplication extends Application {

    private static final String DAILY_SUMMARY_WORK_TAG = "taskie_daily_summary";

    @Override
    public void onCreate() {
        super.onCreate();

        // Create all notification channels (required on Android 8+)
        NotificationHelper.createChannels(this);

        // Schedule the daily 6 AM summary notification
        scheduleDailySummary();
    }

    /**
     * Schedules a repeating WorkManager job that fires every 24 hours,
     * initially delayed so it first runs at the next 6 AM.
     */
    private void scheduleDailySummary() {
        long initialDelay = computeDelayUntil6am();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(DailySummaryWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .addTag(DAILY_SUMMARY_WORK_TAG)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                DAILY_SUMMARY_WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE, // Changed to REPLACE to apply the new 6 AM time
                request
        );
    }

    /** Returns milliseconds until the next 6:00 AM. */
    private long computeDelayUntil6am() {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 6);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        long now = System.currentTimeMillis();
        if (target.getTimeInMillis() <= now) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        return target.getTimeInMillis() - now;
    }
}
