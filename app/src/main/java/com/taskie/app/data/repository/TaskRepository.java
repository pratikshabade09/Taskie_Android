package com.taskie.app.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.taskie.app.data.dao.TaskDao;
import com.taskie.app.data.database.TaskDatabase;
import com.taskie.app.data.model.Task;
import com.taskie.app.notification.NotificationHelper;
import com.taskie.app.utils.DateUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository – the single source of truth for all task data.
 * Runs all database operations on a background executor thread so Room
 * does not block the main (UI) thread.
 */
public class TaskRepository {

    private final TaskDao        taskDao;
    private final ExecutorService executor;
    private final NotificationHelper notificationHelper;

    // ── Observable LiveData streams ──────────────────────────────────────────
    public final LiveData<List<Task>> allActive;
    public final LiveData<List<Task>> allCompleted;
    public final LiveData<List<Task>> recentlyCompleted;

    public TaskRepository(Context context) {
        TaskDatabase db = TaskDatabase.getInstance(context);
        taskDao             = db.taskDao();
        executor            = Executors.newSingleThreadExecutor();
        notificationHelper  = new NotificationHelper(context);

        allActive          = taskDao.getAllActive();
        allCompleted       = taskDao.getAllCompleted();
        recentlyCompleted  = taskDao.getRecentlyCompleted();
    }

    // ── Section queries ───────────────────────────────────────────────────────

    public LiveData<List<Task>> getToday() {
        long[] range = DateUtils.getTodayRange();
        return taskDao.getToday(range[0], range[1]);
    }

    public LiveData<List<Task>> getUpcoming() {
        long[] range = DateUtils.getTodayRange();
        return taskDao.getUpcoming(range[1]);
    }

    public LiveData<List<Task>> getOverdue() {
        return taskDao.getOverdue(System.currentTimeMillis());
    }

    public LiveData<List<Task>> search(String query) {
        return taskDao.search(query);
    }

    public LiveData<Task> observeById(int id) {
        return taskDao.observeById(id);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    public void insert(Task task, InsertCallback callback) {
        executor.execute(() -> {
            long id = taskDao.insert(task);
            task.setId((int) id);
            if (task.getReminderTime() > 0) {
                notificationHelper.scheduleReminder(task);
            }
            if (callback != null) callback.onInserted((int) id);
        });
    }

    public void insert(Task task) {
        insert(task, null);
    }

    public void update(Task task) {
        executor.execute(() -> {
            taskDao.update(task);
            // Re-schedule or cancel notification based on updated reminder time
            notificationHelper.cancelReminder(task.getId());
            if (!task.isCompleted() && task.getReminderTime() > 0) {
                notificationHelper.scheduleReminder(task);
            }
        });
    }

    public void delete(Task task) {
        executor.execute(() -> {
            notificationHelper.cancelReminder(task.getId());
            taskDao.delete(task);
        });
    }

    public void deleteById(int taskId) {
        executor.execute(() -> {
            notificationHelper.cancelReminder(taskId);
            taskDao.deleteById(taskId);
        });
    }

    public void markComplete(Task task) {
        executor.execute(() -> {
            task.setCompleted(true);
            task.setCompletedAt(System.currentTimeMillis());
            notificationHelper.cancelReminder(task.getId());
            taskDao.update(task);
        });
    }

    public void markIncomplete(Task task) {
        executor.execute(() -> {
            task.setCompleted(false);
            task.setCompletedAt(0);
            taskDao.update(task);
            if (task.getReminderTime() > System.currentTimeMillis()) {
                notificationHelper.scheduleReminder(task);
            }
        });
    }

    public void deleteAllCompleted() {
        executor.execute(taskDao::deleteAllCompleted);
    }

    public void deleteAll() {
        executor.execute(taskDao::deleteAll);
    }

    // ── Analytics helpers (run on background) ────────────────────────────────

    public void getCountsAsync(CountCallback callback) {
        executor.execute(() -> {
            long[] range = DateUtils.getTodayRange();
            int active    = taskDao.countActiveToday(range[0], range[1]);
            int completed = taskDao.countCompletedToday(range[0], range[1]);
            long weekAgo  = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
            int weekTotal = taskDao.countCompletedSince(weekAgo);
            callback.onResult(active, completed, weekTotal);
        });
    }

    public void getCompletedInRangeAsync(long from, long to, RangeCallback callback) {
        executor.execute(() -> {
            List<Task> tasks = taskDao.getCompletedInRange(from, to);
            callback.onResult(tasks);
        });
    }

    public void rescheduleAllReminders() {
        executor.execute(() -> {
            List<Task> tasks = taskDao.getTasksWithPendingReminders(System.currentTimeMillis());
            for (Task task : tasks) {
                notificationHelper.scheduleReminder(task);
            }
        });
    }

    // ── Callback interfaces ──────────────────────────────────────────────────

    public interface InsertCallback {
        void onInserted(int newId);
    }

    public interface CountCallback {
        void onResult(int activeToday, int completedToday, int completedThisWeek);
    }

    public interface RangeCallback {
        void onResult(List<Task> tasks);
    }
}
