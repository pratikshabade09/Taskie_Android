package com.taskie.app.ui.dashboard;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.taskie.app.R;
import com.taskie.app.data.model.Task;
import com.taskie.app.ui.main.MainActivity;
import com.taskie.app.utils.SmartSuggestionEngine;
import com.taskie.app.viewmodel.TaskViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main task list screen.
 * Shows tasks grouped into sections: Overdue · Today · Upcoming · Recently Completed.
 * Supports swipe-right to complete and swipe-left to delete with undo.
 */
public class DashboardFragment extends Fragment implements TaskAdapter.TaskClickListener {

    private TaskViewModel taskViewModel;
    private TaskAdapter   adapter;
    private RecyclerView  recyclerView;
    private View          tvEmpty;
    private EditText      etSearch;
    private SmartSuggestionEngine smartEngine;

    // Keep sectioned data
    private List<Task> overdueList   = new ArrayList<>();
    private List<Task> todayList     = new ArrayList<>();
    private List<Task> upcomingList  = new ArrayList<>();
    private List<Task> completedList = new ArrayList<>();

    private boolean isSearching = false;

    public static DashboardFragment newInstance() {
        return new DashboardFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.rv_tasks);
        tvEmpty      = view.findViewById(R.id.tv_empty);
        etSearch     = view.findViewById(R.id.et_search);

        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        smartEngine   = new SmartSuggestionEngine(requireContext());

        // Populate today's date in the header
        TextView tvDateHeader = view.findViewById(R.id.tv_date_header);
        if (tvDateHeader != null) {
            String today = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                    .format(new Date());
            tvDateHeader.setText(today);
        }

        setupRecyclerView();
        setupSwipeActions();
        setupSearch();
        observeData();
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new TaskAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);
    }

    // ── Swipe gestures ────────────────────────────────────────────────────────

    private void setupSwipeActions() {
        SwipeActionCallback swipeCallback = new SwipeActionCallback(requireContext()) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getAdapterPosition();
                Object item  = adapter.getItemAt(position);

                if (!(item instanceof Task)) return;
                Task task = (Task) item;

                if (direction == ItemTouchHelper.RIGHT) {
                    // Swipe right → complete
                    if (!task.isCompleted()) {
                        taskViewModel.markComplete(task);
                        smartEngine.recordCompletion(task.getTags(), System.currentTimeMillis());
                        smartEngine.updateStreak();
                        showUndoSnackbar(task);
                    }
                } else {
                    // Swipe left → delete
                    taskViewModel.deleteTask(task);
                    Snackbar.make(recyclerView, "Task deleted", Snackbar.LENGTH_SHORT).show();
                }
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);
    }

    private void showUndoSnackbar(Task task) {
        Snackbar.make(recyclerView,
                        "\"" + task.getTitle() + "\" completed",
                        Snackbar.LENGTH_LONG)
                .setAction("UNDO", v -> taskViewModel.undoComplete())
                .setActionTextColor(getResources().getColor(R.color.accent, null))
                .show();
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    isSearching = false;
                    rebuildSections();
                } else {
                    isSearching = true;
                    taskViewModel.getSearchResults(query).observe(
                            getViewLifecycleOwner(),
                            tasks -> adapter.submitFlatList(tasks, "Results for \"" + query + "\"")
                    );
                }
            }
        });
    }

    // ── Data observation ──────────────────────────────────────────────────────

    private void observeData() {
        taskViewModel.overdueTasks.observe(getViewLifecycleOwner(), tasks -> {
            overdueList = tasks != null ? tasks : new ArrayList<>();
            if (!isSearching) rebuildSections();
        });

        taskViewModel.todayTasks.observe(getViewLifecycleOwner(), tasks -> {
            todayList = tasks != null ? tasks : new ArrayList<>();
            if (!isSearching) rebuildSections();
        });

        taskViewModel.upcomingTasks.observe(getViewLifecycleOwner(), tasks -> {
            upcomingList = tasks != null ? tasks : new ArrayList<>();
            if (!isSearching) rebuildSections();
        });

        taskViewModel.recentlyCompletedTasks.observe(getViewLifecycleOwner(), tasks -> {
            completedList = tasks != null ? tasks : new ArrayList<>();
            if (!isSearching) rebuildSections();
        });
    }

    /**
     * Rebuilds the flat adapter list from our four section lists.
     * Inserts SectionHeader objects as dividers.
     */
    private void rebuildSections() {
        List<Object> items = new ArrayList<>();

        if (!overdueList.isEmpty()) {
            items.add(new SectionHeader("Overdue", overdueList.size()));
            items.addAll(overdueList);
        }
        if (!todayList.isEmpty()) {
            items.add(new SectionHeader("Today", todayList.size()));
            items.addAll(todayList);
        }
        if (!upcomingList.isEmpty()) {
            items.add(new SectionHeader("Upcoming", upcomingList.size()));
            items.addAll(upcomingList);
        }
        if (!completedList.isEmpty()) {
            items.add(new SectionHeader("Recently Completed", completedList.size()));
            items.addAll(completedList);
        }

        adapter.submitList(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── TaskAdapter.TaskClickListener ─────────────────────────────────────────

    @Override
    public void onTaskClick(Task task) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openEditTask(task.getId());
        }
    }

    @Override
    public void onTaskChecked(Task task, boolean isChecked) {
        if (isChecked) {
            taskViewModel.markComplete(task);
            smartEngine.recordCompletion(task.getTags(), System.currentTimeMillis());
            smartEngine.updateStreak();
            showUndoSnackbar(task);
        } else {
            taskViewModel.markIncomplete(task);
        }
    }

    @Override
    public void onTaskDelete(Task task) {
        taskViewModel.deleteTask(task);
        Snackbar.make(recyclerView, "Task deleted", Snackbar.LENGTH_SHORT).show();
    }

    // ── Simple section header model ───────────────────────────────────────────

    public static class SectionHeader {
        public final String title;
        public final int    count;

        public SectionHeader(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }
}
