package com.taskie.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.taskie.app.data.model.Task;

import java.util.List;

/**
 * Data Access Object for the tasks table.
 * All queries returning LiveData are automatically observed on background threads.
 */
@Dao
public interface TaskDao {

    // ── Write operations ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("DELETE FROM tasks WHERE id = :taskId")
    void deleteById(int taskId);

    @Query("DELETE FROM tasks WHERE is_completed = 1")
    void deleteAllCompleted();

    @Query("DELETE FROM tasks")
    void deleteAll();

    // ── Active task queries ───────────────────────────────────────────────────

    /** All tasks not yet completed, ordered by priority desc then due date asc. */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 ORDER BY priority DESC, due_date ASC")
    LiveData<List<Task>> getAllActive();

    /** Tasks due today (and not completed). */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date >= :dayStart AND due_date < :dayEnd " +
           "ORDER BY priority DESC, due_date ASC")
    LiveData<List<Task>> getToday(long dayStart, long dayEnd);

    /** Tasks due after today (upcoming). */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date >= :dayEnd " +
           "ORDER BY due_date ASC, priority DESC")
    LiveData<List<Task>> getUpcoming(long dayEnd);

    /** Tasks whose due date has passed and are not completed (overdue). */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date > 0 AND due_date < :now " +
           "ORDER BY due_date ASC")
    LiveData<List<Task>> getOverdue(long now);

    // ── Completed task queries ─────────────────────────────────────────────────

    /** All completed tasks, ordered by completion time descending. */
    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY completed_at DESC")
    LiveData<List<Task>> getAllCompleted();

    /** Most recently completed tasks, capped at 10. */
    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY completed_at DESC LIMIT 10")
    LiveData<List<Task>> getRecentlyCompleted();

    // ── Search ──────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' " +
           "ORDER BY is_completed ASC, priority DESC")
    LiveData<List<Task>> search(String query);

    // ── Single task lookup ──────────────────────────────────────────────────────

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task getById(int id);

    @Query("SELECT * FROM tasks WHERE id = :id")
    LiveData<Task> observeById(int id);

    // ── Analytics queries ───────────────────────────────────────────────────────

    /** Count of tasks completed today. */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND completed_at >= :dayStart AND completed_at < :dayEnd")
    int countCompletedToday(long dayStart, long dayEnd);

    /** Count of active tasks today. */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND due_date >= :dayStart AND due_date < :dayEnd")
    int countActiveToday(long dayStart, long dayEnd);

    /** Count of tasks completed per day for the last 7 days – used for streak. */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND completed_at >= :since")
    int countCompletedSince(long since);

    /** All completed tasks in a time range (for analytics chart). */
    @Query("SELECT * FROM tasks WHERE is_completed = 1 AND completed_at >= :from AND completed_at <= :to " +
           "ORDER BY completed_at ASC")
    List<Task> getCompletedInRange(long from, long to);

    /** All tasks with a reminder time set (for rescheduling after boot). */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND reminder_time > :now")
    List<Task> getTasksWithPendingReminders(long now);

    /** Tasks with no due date. */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date = 0 ORDER BY created_at DESC")
    LiveData<List<Task>> getNoDueDate();
}
