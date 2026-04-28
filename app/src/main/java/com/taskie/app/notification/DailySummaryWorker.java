package com.taskie.app.notification;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.taskie.app.data.dao.TaskDao;
import com.taskie.app.data.database.TaskDatabase;
import com.taskie.app.utils.DateUtils;

/**
 * WorkManager periodic worker (fires every 24 h, starting at 8 AM).
 * Queries today's active task count and shows the daily summary notification.
 */
public class DailySummaryWorker extends Worker {

    public DailySummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            TaskDao dao     = TaskDatabase.getInstance(context).taskDao();

            long[] todayRange     = DateUtils.getTodayRange();
            long[] yesterdayRange = DateUtils.getYesterdayRange();

            int activeToday       = dao.countActiveToday(todayRange[0], todayRange[1]);
            int completedYesterday = dao.countCompletedToday(yesterdayRange[0], yesterdayRange[1]);

            NotificationHelper helper = new NotificationHelper(context);
            helper.showDailySummary(activeToday, completedYesterday);

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
