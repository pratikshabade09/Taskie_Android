package com.taskie.app.ui.analytics;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.taskie.app.R;
import com.taskie.app.data.model.Task;
import com.taskie.app.utils.DateUtils;
import com.taskie.app.utils.SmartSuggestionEngine;
import com.taskie.app.viewmodel.TaskViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics screen.
 * Shows:
 *  - Current streak (fire emoji + day count)
 *  - 7-day bar chart of completed tasks (drawn programmatically)
 *  - Smart insight phrases from SmartSuggestionEngine
 *  - Weekly stats (total active, completed, completion rate)
 */
public class AnalyticsFragment extends Fragment {

    private TaskViewModel       taskViewModel;
    private SmartSuggestionEngine smartEngine;

    private TextView    tvStreakCount;
    private TextView    tvStreakLabel;
    private LinearLayout chartContainer;
    private TextView    tvWeeklyCompleted;
    private TextView    tvWeeklyActive;
    private TextView    tvCompletionRate;
    private LinearLayout insightsContainer;

    public static AnalyticsFragment newInstance() {
        return new AnalyticsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analytics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        smartEngine   = new SmartSuggestionEngine(requireContext());

        bindViews(view);
        loadAnalytics();
        loadInsights();
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private void bindViews(View v) {
        tvStreakCount      = v.findViewById(R.id.tv_streak_count);
        tvStreakLabel      = v.findViewById(R.id.tv_streak_label);
        chartContainer     = v.findViewById(R.id.chart_container);
        tvWeeklyCompleted  = v.findViewById(R.id.tv_weekly_completed);
        tvWeeklyActive     = v.findViewById(R.id.tv_weekly_active);
        tvCompletionRate   = v.findViewById(R.id.tv_completion_rate);
        insightsContainer  = v.findViewById(R.id.insights_container);
    }

    // ── Load analytics ────────────────────────────────────────────────────────

    private void loadAnalytics() {
        if (!isAdded()) return;

        int streak = smartEngine.getCurrentStreak();
        tvStreakCount.setText(String.valueOf(streak));
        tvStreakLabel.setText(streak == 1 ? "day streak 🔥" : "day streak 🔥");

        // Load completed tasks from last 7 days for the bar chart
        long now  = System.currentTimeMillis();
        long from = now - 7L * 24 * 60 * 60 * 1000;
        taskViewModel.loadCompletedInRange(from, now, tasks -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> buildChart(tasks));
            }
        });

        // Weekly summary counts
        taskViewModel.loadAnalytics((active, completed, weekTotal) -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    tvWeeklyCompleted.setText(String.valueOf(weekTotal));
                    tvWeeklyActive.setText(String.valueOf(active));
                    int rate = weekTotal + active > 0
                        ? (int)(weekTotal * 100.0 / (weekTotal + active)) : 0;
                    tvCompletionRate.setText(rate + "%");
                });
            }
        });
    }

    // ── Bar chart ─────────────────────────────────────────────────────────────

    private void buildChart(List<Task> tasks) {
        if (!isAdded() || chartContainer == null) return;
        
        chartContainer.removeAllViews();
        if (tasks == null) tasks = new ArrayList<>();

        // Build a map: startOfDay → count
        Map<Long, Integer> countMap = new HashMap<>();
        for (Task t : tasks) {
            long dayStart = DateUtils.startOfDay(t.getCompletedAt());
            countMap.put(dayStart, countMap.getOrDefault(dayStart, 0) + 1);
        }

        // Build 7-day list (last 7 days including today)
        int maxCount = 1;
        long[] days     = new long[7];
        int[]  counts   = new int[7];
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 7; i++) {
            days[6 - i]   = cal.getTimeInMillis();
            counts[6 - i] = countMap.getOrDefault(cal.getTimeInMillis(), 0);
            if (counts[6 - i] > maxCount) maxCount = counts[6 - i];
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        int barMaxHeightDp = 80;
        float density      = getResources().getDisplayMetrics().density;
        int   barMaxHeightPx = (int)(barMaxHeightDp * density);

        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);

            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            colParams.setMargins(4, 0, 4, 0);
            col.setLayoutParams(colParams);

            // Count label above bar
            TextView tvCount = new TextView(requireContext());
            tvCount.setText(counts[i] > 0 ? String.valueOf(counts[i]) : "");
            tvCount.setTextSize(10f);
            tvCount.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvCount.setGravity(android.view.Gravity.CENTER);

            // Bar
            View bar = new View(requireContext());
            int barHeight = counts[i] == 0
                    ? (int)(4 * density)
                    : (int)(barMaxHeightPx * (float) counts[i] / maxCount);
            LinearLayout.LayoutParams barParams =
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight);
            barParams.topMargin = (int)(4 * density);
            bar.setLayoutParams(barParams);
            bar.setBackgroundResource(counts[i] > 0 ? R.drawable.bar_filled : R.drawable.bar_empty);

            // Day label below
            TextView tvDay = new TextView(requireContext());
            tvDay.setText(DateUtils.shortDayLabel(days[i]));
            tvDay.setTextSize(10f);
            tvDay.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvDay.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams dayParams =
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                                                  LinearLayout.LayoutParams.WRAP_CONTENT);
            dayParams.topMargin = (int)(4 * density);
            tvDay.setLayoutParams(dayParams);

            col.addView(tvCount);
            col.addView(bar);
            col.addView(tvDay);
            chartContainer.addView(col);
        }
    }

    // ── Insights ──────────────────────────────────────────────────────────────

    private void loadInsights() {
        if (!isAdded() || insightsContainer == null) return;

        insightsContainer.removeAllViews();
        List<String> insights = smartEngine.getInsights();

        if (insights.isEmpty()) {
            TextView tv = new TextView(requireContext());
            tv.setText("Complete more tasks to unlock insights about your patterns.");
            tv.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tv.setTextSize(13f);
            insightsContainer.addView(tv);
            return;
        }

        for (String insight : insights) {
            TextView tv = new TextView(requireContext());
            tv.setText("💡  " + insight);
            tv.setTextColor(getResources().getColor(R.color.text_primary, null));
            tv.setTextSize(13f);
            int pad = (int)(8 * getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            insightsContainer.addView(tv);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh on every visit
        loadAnalytics();
        loadInsights();
    }
}
