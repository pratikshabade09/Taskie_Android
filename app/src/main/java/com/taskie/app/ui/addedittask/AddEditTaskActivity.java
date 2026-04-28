package com.taskie.app.ui.addedittask;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.taskie.app.R;
import com.taskie.app.data.model.Task;
import com.taskie.app.utils.DateUtils;
import com.taskie.app.utils.SmartSuggestionEngine;
import com.taskie.app.viewmodel.TaskViewModel;

import java.util.Calendar;

/**
 * Activity for creating new tasks and editing existing ones.
 * Smart auto-detection of priority based on title keywords.
 */
public class AddEditTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "extra_task_id";
    private static final int NO_TASK = -1;

    private TaskViewModel taskViewModel;
    private SmartSuggestionEngine smartEngine;

    // Form fields
    private EditText          etTitle;
    private EditText          etDescription;
    private TextView          tvDueDate;
    private TextView          tvReminderTime;
    private RadioGroup        rgPriority;
    private ChipGroup         cgTags;
    private Button            btnSave;

    // State
    private int    editingTaskId   = NO_TASK;
    private long   selectedDueDate = 0;
    private long   selectedReminder = 0;
    private Task   existingTask    = null;

    private final String[] ALL_TAGS = {
        Task.TAG_WORK, Task.TAG_STUDY, Task.TAG_PERSONAL,
        Task.TAG_HEALTH, Task.TAG_FINANCE, Task.TAG_OTHER
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_task);

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        smartEngine   = new SmartSuggestionEngine(this);

        bindViews();
        setupToolbar();
        buildTagChips();
        setupDateTimePickers();
        setupPriority();
        setupSmartDetection();

        editingTaskId = getIntent().getIntExtra(EXTRA_TASK_ID, NO_TASK);
        if (editingTaskId != NO_TASK) {
            loadExistingTask(editingTaskId);
        }

        btnSave.setOnClickListener(v -> saveTask());
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        etTitle         = findViewById(R.id.et_title);
        etDescription   = findViewById(R.id.et_description);
        tvDueDate       = findViewById(R.id.tv_due_date);
        tvReminderTime  = findViewById(R.id.tv_reminder_time);
        rgPriority      = findViewById(R.id.rg_priority);
        cgTags          = findViewById(R.id.cg_tags);
        btnSave         = findViewById(R.id.btn_save);
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(editingTaskId == NO_TASK ? "New Task" : "Edit Task");
        }
    }

    // ── Tag chips ─────────────────────────────────────────────────────────────

    private void buildTagChips() {
        cgTags.removeAllViews();
        for (String tag : ALL_TAGS) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.chip_background);
            chip.setCheckedIconVisible(true);
            cgTags.addView(chip);
        }
    }

    // ── Date & Time pickers ───────────────────────────────────────────────────

    private void setupDateTimePickers() {
        tvDueDate.setOnClickListener(v -> showDateTimePicker(false));
        tvReminderTime.setOnClickListener(v -> showDateTimePicker(true));
    }

    private void showDateTimePicker(boolean isReminder) {
        Calendar cal = Calendar.getInstance();
        if (!isReminder && selectedDueDate > 0) cal.setTimeInMillis(selectedDueDate);

        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day);

            new TimePickerDialog(this, (tp, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour);
                selected.set(Calendar.MINUTE, minute);
                selected.set(Calendar.SECOND, 0);
                long ts = selected.getTimeInMillis();

                if (isReminder) {
                    selectedReminder = ts;
                    tvReminderTime.setText("⏰ " + DateUtils.formatDateTime(ts));
                } else {
                    selectedDueDate = ts;
                    tvDueDate.setText("📅 " + DateUtils.formatDateTime(ts));
                    // Auto-suggest reminder 15 minutes before
                    if (selectedReminder == 0) {
                        selectedReminder = ts - 15 * 60 * 1000;
                        tvReminderTime.setText("⏰ " + DateUtils.formatDateTime(selectedReminder));
                    }
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ── Priority auto-detection ───────────────────────────────────────────────

    private void setupPriority() {
        // Default to medium
        rgPriority.check(R.id.rb_medium);
    }

    private void setupSmartDetection() {
        etTitle.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String title = etTitle.getText().toString();
                int detectedPriority = smartEngine.detectPriorityFromTitle(title);
                switch (detectedPriority) {
                    case Task.PRIORITY_HIGH:
                        rgPriority.check(R.id.rb_high);
                        break;
                    case Task.PRIORITY_MEDIUM:
                        rgPriority.check(R.id.rb_medium);
                        break;
                    default:
                        rgPriority.check(R.id.rb_low);
                }
            }
        });
    }

    // ── Load existing task ────────────────────────────────────────────────────

    private void loadExistingTask(int taskId) {
        taskViewModel.observeTask(taskId).observe(this, task -> {
            if (task != null && existingTask == null) {
                existingTask = task;
                populateForm(task);
            }
        });
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Task");
        }
    }

    private void populateForm(Task task) {
        etTitle.setText(task.getTitle());
        etDescription.setText(task.getDescription());

        if (task.getDueDate() > 0) {
            selectedDueDate = task.getDueDate();
            tvDueDate.setText("📅 " + DateUtils.formatDateTime(task.getDueDate()));
        }
        if (task.getReminderTime() > 0) {
            selectedReminder = task.getReminderTime();
            tvReminderTime.setText("⏰ " + DateUtils.formatDateTime(task.getReminderTime()));
        }

        switch (task.getPriority()) {
            case Task.PRIORITY_HIGH:   rgPriority.check(R.id.rb_high);   break;
            case Task.PRIORITY_MEDIUM: rgPriority.check(R.id.rb_medium); break;
            default:                   rgPriority.check(R.id.rb_low);    break;
        }

        if (task.getTags() != null) {
            String[] selectedTags = task.getTags().split(",");
            for (int i = 0; i < cgTags.getChildCount(); i++) {
                Chip chip = (Chip) cgTags.getChildAt(i);
                for (String tag : selectedTags) {
                    if (chip.getText().toString().equals(tag.trim())) {
                        chip.setChecked(true);
                    }
                }
            }
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title is required");
            return;
        }

        String description = etDescription.getText().toString().trim();
        int    priority    = getSelectedPriority();
        String tags        = getSelectedTags();

        if (editingTaskId == NO_TASK) {
            // Create new task
            Task task = new Task(title, description, selectedDueDate, selectedReminder, priority, tags);
            taskViewModel.insertTask(task);
            Toast.makeText(this, "Task added!", Toast.LENGTH_SHORT).show();
        } else {
            // Update existing task
            if (existingTask != null) {
                existingTask.setTitle(title);
                existingTask.setDescription(description);
                existingTask.setDueDate(selectedDueDate);
                existingTask.setReminderTime(selectedReminder);
                existingTask.setPriority(priority);
                existingTask.setTags(tags);
                taskViewModel.updateTask(existingTask);
                Toast.makeText(this, "Task updated!", Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    private int getSelectedPriority() {
        int checkedId = rgPriority.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_high)   return Task.PRIORITY_HIGH;
        if (checkedId == R.id.rb_medium) return Task.PRIORITY_MEDIUM;
        return Task.PRIORITY_LOW;
    }

    private String getSelectedTags() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cgTags.getChildCount(); i++) {
            Chip chip = (Chip) cgTags.getChildAt(i);
            if (chip.isChecked()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(chip.getText().toString());
            }
        }
        return sb.toString();
    }
}
