package com.taskie.app.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.taskie.app.data.model.Task;
import com.taskie.app.data.repository.TaskRepository;

import java.util.List;

/**
 * Shared ViewModel – survives configuration changes and provides
 * reactive task streams to all UI fragments through LiveData.
 */
public class TaskViewModel extends AndroidViewModel {

    private final TaskRepository repository;

    // ── LiveData streams ──────────────────────────────────────────────────────
    public final LiveData<List<Task>> todayTasks;
    public final LiveData<List<Task>> upcomingTasks;
    public final LiveData<List<Task>> overdueTasks;
    public final LiveData<List<Task>> recentlyCompletedTasks;

    /** Carries the last voice-parsed task so VoiceFragment can confirm it. */
    private final MutableLiveData<Task> pendingVoiceTask = new MutableLiveData<>();

    /** Search results – driven by a separate query. */
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private LiveData<List<Task>> searchResults;

    /** Snackbar undo payload – holds the last completed task. */
    private final MutableLiveData<Task> undoTask = new MutableLiveData<>();

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository           = new TaskRepository(application);
        todayTasks           = repository.getToday();
        upcomingTasks        = repository.getUpcoming();
        overdueTasks         = repository.getOverdue();
        recentlyCompletedTasks = repository.recentlyCompleted;
    }

    // ── Write operations ──────────────────────────────────────────────────────

    public void insertTask(Task task) {
        repository.insert(task);
    }

    public void insertTask(Task task, TaskRepository.InsertCallback callback) {
        repository.insert(task, callback);
    }

    public void updateTask(Task task) {
        repository.update(task);
    }

    public void deleteTask(Task task) {
        repository.delete(task);
    }

    public void deleteById(int taskId) {
        repository.deleteById(taskId);
    }

    public void markComplete(Task task) {
        undoTask.postValue(task);
        repository.markComplete(task);
    }

    public void markIncomplete(Task task) {
        repository.markIncomplete(task);
    }

    public void undoComplete() {
        Task task = undoTask.getValue();
        if (task != null) {
            repository.markIncomplete(task);
            undoTask.postValue(null);
        }
    }

    public void deleteAllCompleted() {
        repository.deleteAllCompleted();
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    // ── Voice task pipeline ──────────────────────────────────────────────────

    public void setPendingVoiceTask(Task task) {
        // Ensure this is on the main thread for observers
        if (Looper.myLooper() == Looper.getMainLooper()) {
            pendingVoiceTask.setValue(task);
        } else {
            pendingVoiceTask.postValue(task);
        }
    }

    public LiveData<Task> getPendingVoiceTask() {
        return pendingVoiceTask;
    }

    public void confirmVoiceTask() {
        Task task = pendingVoiceTask.getValue();
        if (task != null) {
            repository.insert(task);
            pendingVoiceTask.postValue(null);
        }
    }

    public void discardVoiceTask() {
        pendingVoiceTask.postValue(null);
    }

    // ── Search ───────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getSearchResults(String query) {
        searchResults = repository.search(query);
        return searchResults;
    }

    // ── Analytics ────────────────────────────────────────────────────────────

    public void loadAnalytics(TaskRepository.CountCallback callback) {
        repository.getCountsAsync(callback);
    }

    public void loadCompletedInRange(long from, long to, TaskRepository.RangeCallback callback) {
        repository.getCompletedInRangeAsync(from, to, callback);
    }

    // ── Undo helper ──────────────────────────────────────────────────────────

    public LiveData<Task> getUndoTask() {
        return undoTask;
    }

    public void clearUndo() {
        undoTask.postValue(null);
    }

    // ── Observe single task ───────────────────────────────────────────────────

    public LiveData<Task> observeTask(int taskId) {
        return repository.observeById(taskId);
    }
}
