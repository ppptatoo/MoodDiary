package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.utils.AiInsightHelper;
import com.hearttrace.mooddiary.utils.DoubaoApiClient;
import com.hearttrace.mooddiary.view.IntensityBarChartView;
import com.hearttrace.mooddiary.view.MoodPieChartView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {

    private static final String[] MOOD_LABELS = {"开心", "难过", "生气", "平静", "焦虑", "疲惫"};
    private static final int[] MOOD_COLORS = {
            R.color.mood_happy,
            R.color.mood_sad,
            R.color.mood_angry,
            R.color.mood_calm,
            R.color.mood_anxious,
            R.color.mood_tired
    };

    private long userId;
    private AppDatabase db;
    private ExecutorService executor;
    private final DoubaoApiClient apiClient = new DoubaoApiClient();

    private MoodPieChartView pieChart;
    private IntensityBarChartView barChart;
    private GridLayout gridMoodLegend;
    private TextView tvMonthDays, tvMonthDaysSub;
    private TextView tvDominantMood, tvDominantPercent;
    private TextView tvAvgIntensity, tvIntensityHint;
    private TextView tvWeekDays, tvWeekDaysSub;
    private TextView tvInsightWeek, tvInsightMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        userId = getIntent().getLongExtra("USER_ID", -1);
        if (userId == -1) {
            finish();
            return;
        }

        bindViews();
        setupBottomNav();

        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        loadStatistics();
    }

    private void bindViews() {
        pieChart = findViewById(R.id.pie_chart);
        barChart = findViewById(R.id.bar_chart);
        gridMoodLegend = findViewById(R.id.grid_mood_legend);
        tvMonthDays = findViewById(R.id.tv_month_days);
        tvMonthDaysSub = findViewById(R.id.tv_month_days_sub);
        tvDominantMood = findViewById(R.id.tv_dominant_mood);
        tvDominantPercent = findViewById(R.id.tv_dominant_percent);
        tvAvgIntensity = findViewById(R.id.tv_avg_intensity);
        tvIntensityHint = findViewById(R.id.tv_intensity_hint);
        tvWeekDays = findViewById(R.id.tv_week_days);
        tvWeekDaysSub = findViewById(R.id.tv_week_days_sub);
        tvInsightWeek = findViewById(R.id.tv_insight_week);
        tvInsightMonth = findViewById(R.id.tv_insight_month);
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_statistics);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_statistics) {
                return true;
            }
            Intent intent = null;
            if (id == R.id.nav_calendar) {
                intent = new Intent(this, HomeActivity.class);
            } else if (id == R.id.nav_history) {
                intent = new Intent(this, DiaryListActivity.class);
            } else if (id == R.id.nav_profile) {
                intent = new Intent(this, ProfileActivity.class);
            }
            if (intent != null) {
                intent.putExtra("USER_ID", userId);
                startActivity(intent);
                finish();
            }
            return false;
        });
    }

    private void loadStatistics() {
        executor.execute(() -> {
            List<MoodEntry> all = db.moodEntryDao().getMoodEntriesByUserId(userId);

            Calendar now = Calendar.getInstance();
            long weekStart = startOfWeek(now);
            long monthStart = startOfMonth(now);

            Map<String, Integer> monthMoodCounts = initMoodCounts();
            Map<String, Integer> weekMoodCounts = initMoodCounts();
            Map<Integer, List<Float>> dayIntensities = new HashMap<>();

            int monthDistinctDays = 0;
            int weekDistinctDays = 0;
            java.util.Set<Integer> monthDays = new java.util.HashSet<>();
            java.util.Set<Integer> weekDays = new java.util.HashSet<>();

            float monthIntensitySum = 0;
            int monthEntryCount = 0;

            for (MoodEntry entry : all) {
                long ts = entry.getTimestamp();
                String mood = normalizeMood(entry.getUserEmotionLabel());
                float intensity = moodToIntensity(mood);

                if (ts >= monthStart) {
                    monthMoodCounts.put(mood, monthMoodCounts.get(mood) + 1);
                    monthIntensitySum += intensity;
                    monthEntryCount++;

                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(ts);
                    int day = c.get(Calendar.DAY_OF_MONTH);
                    monthDays.add(day);
                    if (!dayIntensities.containsKey(day)) {
                        dayIntensities.put(day, new ArrayList<>());
                    }
                    dayIntensities.get(day).add(intensity);
                }

                if (ts >= weekStart) {
                    weekMoodCounts.put(mood, weekMoodCounts.get(mood) + 1);
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(ts);
                    weekDays.add(c.get(Calendar.DAY_OF_YEAR));
                }
            }

            monthDistinctDays = monthDays.size();
            weekDistinctDays = weekDays.size();

            float avgIntensity = monthEntryCount > 0
                    ? monthIntensitySum / monthEntryCount : 0f;

            String dominant = findDominant(monthMoodCounts);
            int dominantCount = monthMoodCounts.getOrDefault(dominant, 0);
            int monthTotal = sumCounts(monthMoodCounts);
            int dominantPercent = monthTotal > 0
                    ? Math.round(100f * dominantCount / monthTotal) : 0;

            List<MoodPieChartView.Slice> pieSlices = buildPieSlices(monthMoodCounts);
            List<IntensityBarChartView.BarPoint> barPoints = buildBarPoints(dayIntensities);

            String weekSummary = buildSummary("本周", weekMoodCounts, weekDistinctDays, all, weekStart);
            String monthSummary = buildSummary("本月", monthMoodCounts, monthDistinctDays, all, monthStart);

            final int fMonthDays = monthDistinctDays;
            final int fWeekDays = weekDistinctDays;
            final float fAvg = avgIntensity;
            final String fDominant = dominant;
            final int fPercent = dominantPercent;

            runOnUiThread(() -> {
                pieChart.setData(pieSlices);
                bindMoodLegend(monthMoodCounts);
                barChart.setData(barPoints);

                tvMonthDays.setText(String.valueOf(fMonthDays));
                tvMonthDaysSub.setText(String.format(Locale.getDefault(),
                        "共记录%d天日记", fMonthDays));
                tvDominantMood.setText(fDominant);
                tvDominantPercent.setText(String.format(Locale.getDefault(),
                        "占比%d%%", fPercent));
                tvAvgIntensity.setText(String.format(Locale.getDefault(), "%.2f", fAvg));
                tvIntensityHint.setText(intensityHint(fAvg));
                tvWeekDays.setText(String.valueOf(fWeekDays));
                tvWeekDaysSub.setText(String.format(Locale.getDefault(),
                        "本周共记录%d天", fWeekDays));
            });

            requestAiInsight("本周", weekSummary, tvInsightWeek);
            requestAiInsight("本月", monthSummary, tvInsightMonth);
        });
    }

    private void bindMoodLegend(Map<String, Integer> counts) {
        gridMoodLegend.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < MOOD_LABELS.length; i++) {
            String label = MOOD_LABELS[i];
            View row = inflater.inflate(R.layout.item_mood_legend, gridMoodLegend, false);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(i % 3, 1f);
            lp.rowSpec = GridLayout.spec(i / 3);
            lp.setMargins(4, 4, 4, 4);
            row.setLayoutParams(lp);

            View dot = row.findViewById(R.id.view_dot);
            TextView tvName = row.findViewById(R.id.tv_mood_name);
            TextView tvCount = row.findViewById(R.id.tv_mood_count);

            int color = ContextCompat.getColor(this, MOOD_COLORS[i]);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            dot.setBackground(gd);

            int count = counts.getOrDefault(label, 0);
            tvName.setText(label);
            tvCount.setText(count + "次");
            gridMoodLegend.addView(row);
        }
    }

    private List<MoodPieChartView.Slice> buildPieSlices(Map<String, Integer> counts) {
        List<MoodPieChartView.Slice> slices = new ArrayList<>();
        for (int i = 0; i < MOOD_LABELS.length; i++) {
            int c = counts.getOrDefault(MOOD_LABELS[i], 0);
            if (c > 0) {
                slices.add(new MoodPieChartView.Slice(
                        MOOD_LABELS[i], c,
                        ContextCompat.getColor(this, MOOD_COLORS[i])));
            }
        }
        return slices;
    }

    private List<IntensityBarChartView.BarPoint> buildBarPoints(
            Map<Integer, List<Float>> dayIntensities) {
        List<Integer> days = new ArrayList<>(dayIntensities.keySet());
        java.util.Collections.sort(days);
        List<IntensityBarChartView.BarPoint> points = new ArrayList<>();
        if (days.size() > 8) {
            int step = Math.max(1, days.size() / 7);
            for (int i = 0; i < days.size(); i += step) {
                int day = days.get(i);
                points.add(new IntensityBarChartView.BarPoint(day, avg(dayIntensities.get(day))));
            }
            if (points.isEmpty() || points.get(points.size() - 1).day != days.get(days.size() - 1)) {
                int last = days.get(days.size() - 1);
                points.add(new IntensityBarChartView.BarPoint(last, avg(dayIntensities.get(last))));
            }
        } else {
            for (int day : days) {
                points.add(new IntensityBarChartView.BarPoint(day, avg(dayIntensities.get(day))));
            }
        }
        return points;
    }

    private float avg(List<Float> list) {
        if (list == null || list.isEmpty()) return 0f;
        float s = 0;
        for (float v : list) s += v;
        return s / list.size();
    }

    private void requestAiInsight(String period, String summary, TextView target) {
        AiInsightHelper.requestInsight(executor, apiClient, period, summary,
                new AiInsightHelper.Callback() {
                    @Override
                    public void onSuccess(String insight) {
                        target.setText(insight);
                    }

                    @Override
                    public void onError(String message) {
                        target.setText(message);
                    }
                });
    }

    private String buildSummary(String period, Map<String, Integer> moodCounts,
                                int distinctDays, List<MoodEntry> all, long since) {
        StringBuilder sb = new StringBuilder();
        sb.append(period).append("共记录").append(distinctDays).append("天。");
        for (String label : MOOD_LABELS) {
            sb.append(label).append(moodCounts.getOrDefault(label, 0)).append("次、");
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '、') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("。");
        sb.append("主导情绪：").append(findDominant(moodCounts)).append("。");

        int snippet = 0;
        for (MoodEntry e : all) {
            if (e.getTimestamp() >= since && e.getText() != null && !e.getText().isEmpty()) {
                String t = e.getText().trim();
                if (t.length() > 40) t = t.substring(0, 40) + "…";
                sb.append("日记摘录：").append(t).append(" ");
                if (++snippet >= 2) break;
            }
        }
        return sb.toString();
    }

    private static Map<String, Integer> initMoodCounts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String label : MOOD_LABELS) {
            m.put(label, 0);
        }
        return m;
    }

    private static String normalizeMood(String mood) {
        if (mood == null || mood.isEmpty()) return "平静";
        for (String label : MOOD_LABELS) {
            if (label.equals(mood)) return mood;
        }
        return "平静";
    }

    private static float moodToIntensity(String mood) {
        switch (mood) {
            case "开心": return 0.85f;
            case "平静": return 0.55f;
            case "难过": return 0.35f;
            case "生气": return 0.75f;
            case "焦虑": return 0.65f;
            case "疲惫": return 0.45f;
            default: return 0.5f;
        }
    }

    private static String findDominant(Map<String, Integer> counts) {
        String best = "平静";
        int max = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return max <= 0 ? "—" : best;
    }

    private static int sumCounts(Map<String, Integer> counts) {
        int s = 0;
        for (int v : counts.values()) s += v;
        return s;
    }

    private static String intensityHint(float avg) {
        if (avg >= 0.75f) return "整体情绪较为活跃";
        if (avg >= 0.55f) return "整体情绪偏稳定";
        if (avg >= 0.35f) return "整体情绪略偏低落";
        return "近期宜多关爱自己";
    }

    private static long startOfMonth(Calendar ref) {
        Calendar c = (Calendar) ref.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long startOfWeek(Calendar ref) {
        Calendar c = (Calendar) ref.clone();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.after(ref)) {
            c.add(Calendar.WEEK_OF_YEAR, -1);
        }
        return c.getTimeInMillis();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }
}
