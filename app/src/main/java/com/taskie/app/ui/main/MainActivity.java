package com.taskie.app.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.taskie.app.R;
import com.taskie.app.ui.addedittask.AddEditTaskActivity;
import com.taskie.app.ui.analytics.AnalyticsFragment;
import com.taskie.app.ui.dashboard.DashboardFragment;
import com.taskie.app.ui.settings.SettingsFragment;
import com.taskie.app.ui.voice.VoiceFragment;
import com.taskie.app.viewmodel.TaskViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-activity host.
 * Manages bottom navigation and the FAB; fragments swap in/out.
 */
public class MainActivity extends AppCompatActivity {

    private TaskViewModel taskViewModel;
    private FloatingActionButton fab;
    private BottomNavigationView bottomNav;

    // Keep references to avoid re-creating fragments on every switch
    private DashboardFragment  dashboardFragment;
    private VoiceFragment      voiceFragment;
    private AnalyticsFragment  analyticsFragment;
    private SettingsFragment   settingsFragment;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Permissions handled
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        fab       = findViewById(R.id.fab_add_task);
        bottomNav = findViewById(R.id.bottom_navigation);

        setupFragments();
        setupFab();
        setupBottomNav();

        // Default to Dashboard
        if (savedInstanceState == null) {
            showFragment(getDashboardFragment());
            scheduleDailySummary(); // 🔥 THIS LINE ADDED
        }

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!permissions.isEmpty()) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    // ── Fragment Management ───────────────────────────────────────────────────

    private void setupFragments() {
        dashboardFragment  = DashboardFragment.newInstance();
        voiceFragment      = VoiceFragment.newInstance();
        analyticsFragment  = AnalyticsFragment.newInstance();
        settingsFragment   = SettingsFragment.newInstance();
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();

        // FAB visible only on Dashboard
        if (fragment instanceof DashboardFragment) {
            fab.show();
        } else {
            fab.hide();
        }
    }

    private DashboardFragment getDashboardFragment() {
        return dashboardFragment != null ? dashboardFragment : DashboardFragment.newInstance();
    }

    // ── FAB ───────────────────────────────────────────────────────────────────

    private void setupFab() {
        fab.setOnClickListener(v -> openAddTask());
    }

    private void openAddTask() {
        Intent intent = new Intent(this, AddEditTaskActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_up, R.anim.stay);
    }

    // ── Bottom Navigation ─────────────────────────────────────────────────────

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                showFragment(dashboardFragment);
            } else if (id == R.id.nav_voice) {
                showFragment(voiceFragment);
            } else if (id == R.id.nav_analytics) {
                showFragment(analyticsFragment);
            } else if (id == R.id.nav_settings) {
                showFragment(settingsFragment);
            }
            return true;
        });
    }

    // ── Called from DashboardFragment when a task card is tapped ─────────────

    public void openEditTask(int taskId) {
        Intent intent = new Intent(this, AddEditTaskActivity.class);
        intent.putExtra(AddEditTaskActivity.EXTRA_TASK_ID, taskId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_up, R.anim.stay);
    }

    private void scheduleDailySummary() {

        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();

        target.set(Calendar.HOUR_OF_DAY, 6);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        // If current time passed 6 AM → schedule tomorrow
        if (now.after(target)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }

        long delay = target.getTimeInMillis() - now.getTimeInMillis();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        com.taskie.app.notification.DailySummaryWorker.class,
                        24,
                        TimeUnit.HOURS
                )
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "daily_summary",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
        );
    }
}
