package com.taskie.app.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.taskie.app.R;
import com.taskie.app.viewmodel.TaskViewModel;

/**
 * Settings screen.
 * Persists all user preferences to SharedPreferences.
 */
public class SettingsFragment extends Fragment {

    public static final String PREF_VOICE_ENABLED    = "pref_voice_enabled";
    public static final String PREF_WAKE_PHRASE      = "pref_wake_phrase";
    public static final String PREF_NOTIF_ENABLED    = "pref_notifications_enabled";
    public static final String PREF_DAILY_SUMMARY    = "pref_daily_summary";
    public static final String PREF_SMART_REMINDERS  = "pref_smart_reminders";
    public static final String PREF_THEME            = "pref_theme";  // "dark" | "light"

    private SharedPreferences prefs;
    private TaskViewModel taskViewModel;

    // UI refs
    private Switch  switchVoice;
    private Switch  switchNotifications;
    private Switch  switchDailySummary;
    private Switch  switchSmartReminders;
    private EditText etWakePhrase;
    private Button  btnClearCompleted;
    private Button  btnResetAll;
    private TextView tvAppVersion;
    private TextView tvStreakCount;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs         = PreferenceManager.getDefaultSharedPreferences(requireContext());
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        bindViews(view);
        loadPreferences();
        setupListeners();
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private void bindViews(View v) {
        switchVoice          = v.findViewById(R.id.switch_voice);
        switchNotifications  = v.findViewById(R.id.switch_notifications);
        switchDailySummary   = v.findViewById(R.id.switch_daily_summary);
        switchSmartReminders = v.findViewById(R.id.switch_smart_reminders);
        etWakePhrase         = v.findViewById(R.id.et_wake_phrase);
        btnClearCompleted    = v.findViewById(R.id.btn_clear_completed);
        btnResetAll          = v.findViewById(R.id.btn_reset_all);
        tvAppVersion         = v.findViewById(R.id.tv_app_version);
        tvStreakCount        = v.findViewById(R.id.tv_streak_count);
    }

    // ── Load saved preferences ────────────────────────────────────────────────

    private void loadPreferences() {
        switchVoice.setChecked(prefs.getBoolean(PREF_VOICE_ENABLED, true));
        switchNotifications.setChecked(prefs.getBoolean(PREF_NOTIF_ENABLED, true));
        switchDailySummary.setChecked(prefs.getBoolean(PREF_DAILY_SUMMARY, true));
        switchSmartReminders.setChecked(prefs.getBoolean(PREF_SMART_REMINDERS, true));
        etWakePhrase.setText(prefs.getString(PREF_WAKE_PHRASE, "Hey Taskie"));

        // Voice section enabled/disabled
        etWakePhrase.setEnabled(switchVoice.isChecked());

        // App version
        String version;
        try {
            version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "1.0";
        }
        tvAppVersion.setText("Taskie v" + version);

        // Streak
        int streak = prefs.getInt("current_streak", 0);
        tvStreakCount.setText(streak + " day" + (streak != 1 ? "s" : ""));
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        switchVoice.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(PREF_VOICE_ENABLED, checked).apply();
            etWakePhrase.setEnabled(checked);
        });

        switchNotifications.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(PREF_NOTIF_ENABLED, checked).apply());

        switchDailySummary.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(PREF_DAILY_SUMMARY, checked).apply());

        switchSmartReminders.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(PREF_SMART_REMINDERS, checked).apply());

        etWakePhrase.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String phrase = etWakePhrase.getText().toString().trim();
                if (!phrase.isEmpty()) {
                    prefs.edit().putString(PREF_WAKE_PHRASE, phrase).apply();
                }
            }
        });

        btnClearCompleted.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Clear completed tasks?")
                        .setMessage("This will permanently delete all completed tasks.")
                        .setPositiveButton("Clear", (dialog, which) -> {
                            taskViewModel.deleteAllCompleted();
                            Toast.makeText(requireContext(),
                                    "Completed tasks cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        btnResetAll.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Reset all data?")
                        .setMessage("This will permanently delete ALL tasks and preferences. This cannot be undone.")
                        .setPositiveButton("Reset", (dialog, which) -> {
                            taskViewModel.deleteAll();
                            prefs.edit().clear().apply();
                            loadPreferences(); // re-apply defaults
                            Toast.makeText(requireContext(),
                                    "All data cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
    }
}
