package com.example.pose;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.*;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout llStreakContainer;
    private TextView tvReportDate, tvHomeTotalSets, tvHomeMusclesHit, tvStreakCount, tvStreakMessage;
    private ProgressBar pbStreak;
    private MuscleHeatmapView homeMuscleHeatmap;
    private View cvDailyReport;
    private Map<String, List<ExerciseSession>> last7DaysData;
    private final SimpleDateFormat dayNameFormat = new SimpleDateFormat("EEE", Locale.US);
    private final SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private String selectedDateKey;
    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNavigation;
    private List<WorkoutManager.ExerciseMuscleInfo> muscleInfoList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_home);

        // Handle Window Insets
        View rootView = findViewById(R.id.home_root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        llStreakContainer = findViewById(R.id.llStreakContainer);
        tvReportDate = findViewById(R.id.tvReportDate);
        tvHomeTotalSets = findViewById(R.id.tvHomeTotalSets);
        tvHomeMusclesHit = findViewById(R.id.tvHomeMusclesHit);
        tvStreakCount = findViewById(R.id.tvStreakCount);
        tvStreakMessage = findViewById(R.id.tvStreakMessage);
        pbStreak = findViewById(R.id.pbStreak);
        homeMuscleHeatmap = findViewById(R.id.homeMuscleHeatmap);
        cvDailyReport = findViewById(R.id.cvDailyReport);

        if (cvDailyReport != null) {
            cvDailyReport.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, DailyReportActivity.class);
                intent.putExtra("selected_date", selectedDateKey);
                startActivity(intent);
            });
        }

        Button btnStartWorkout = findViewById(R.id.btnStartWorkout);
        if (btnStartWorkout != null) {
            btnStartWorkout.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(intent);
            });
        }

        bottomNavigation = findViewById(R.id.bottomNavigation);
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.navigation_workout);
            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navigation_logout) {
                    mAuth.signOut();
                    startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                    finish();
                    return true;
                } else if (itemId == R.id.navigation_progress) {
                    Intent intent = new Intent(HomeActivity.this, DailyReportActivity.class);
                    intent.putExtra("selected_date", fullDateFormat.format(new Date()));
                    startActivity(intent);
                    return true;
                }
                return true;
            });
        }

        muscleInfoList = WorkoutManager.getExerciseMuscleInfo(this);
        loadAndDisplayData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() != null) {
            if (bottomNavigation != null) {
                bottomNavigation.setSelectedItemId(R.id.navigation_workout);
            }
            loadAndDisplayData();
        }
    }

    private void loadAndDisplayData() {
        last7DaysData = WorkoutManager.getLast7DaysSessions(this);
        Map<String, List<ExerciseSession>> monthData = WorkoutManager.getMonthSessions(this);

        // Monthly Progress Calculation
        int totalSetsThisMonth = 0;
        for (List<ExerciseSession> sessions : monthData.values()) {
            for (ExerciseSession s : sessions) totalSetsThisMonth += s.getCompletedSets().size();
        }

        // Update Streak & Monthly Info
        int currentStreak = WorkoutManager.calculateCurrentStreak(this);
        if (tvStreakCount != null) {
            tvStreakCount.setText(currentStreak + " Day Streak");
        }

        // Monthly Goal Progress (e.g., 50 sets a month)
        int monthlyGoal = 50;
        if (pbStreak != null) {
            pbStreak.setMax(monthlyGoal);
            pbStreak.setProgress(Math.min(monthlyGoal, totalSetsThisMonth));
        }

        if (tvStreakMessage != null) {
            if (totalSetsThisMonth == 0) {
                tvStreakMessage.setText("Start your first session this month!");
            } else {
                tvStreakMessage.setText(totalSetsThisMonth + " sets done this month!");
            }
        }

        renderStreakCircles();

        String today = fullDateFormat.format(new Date());
        displayDayReport(today, "Today's Report");
    }

    private void renderStreakCircles() {
        if (llStreakContainer == null) return;
        llStreakContainer.removeAllViews();
        String todayStr = fullDateFormat.format(new Date());

        List<String> sortedDates = new ArrayList<>(last7DaysData.keySet());
        Collections.sort(sortedDates);

        for (String dateKey : sortedDates) {
            List<ExerciseSession> sessions = last7DaysData.get(dateKey);
            boolean hasWorkout = sessions != null && !sessions.isEmpty();
            boolean isToday = dateKey.equals(todayStr);

            LinearLayout dayLayout = new LinearLayout(this);
            dayLayout.setOrientation(LinearLayout.VERTICAL);
            dayLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            dayLayout.setLayoutParams(lp);

            TextView tvDayName = new TextView(this);
            try {
                Date d = fullDateFormat.parse(dateKey);
                tvDayName.setText(dayNameFormat.format(d).toUpperCase());
            } catch (Exception e) { tvDayName.setText(""); }
            tvDayName.setTextColor(Color.parseColor("#B3FFFFFF"));
            tvDayName.setTextSize(10);
            dayLayout.addView(tvDayName);

            View circle = new View(this);
            int size = (int) (36 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(size, size);
            circleLp.setMargins(0, 8, 0, 0);
            circle.setLayoutParams(circleLp);

            if (isToday) {
                circle.setBackgroundResource(R.drawable.bg_circle_highlight);
            } else if (hasWorkout) {
                circle.setBackgroundResource(R.drawable.bg_circle_filled);
            } else {
                circle.setBackgroundResource(R.drawable.bg_circle_empty);
            }

            circle.setOnClickListener(v -> {
                String label = isToday ? "Today's Report" : "Report for " + tvDayName.getText();
                displayDayReport(dateKey, label);
                highlightSelectedCircle(dayLayout);
            });

            dayLayout.addView(circle);
            llStreakContainer.addView(dayLayout);

            if (isToday) highlightSelectedCircle(dayLayout);
        }
    }

    private void displayDayReport(String dateKey, String label) {
        this.selectedDateKey = dateKey;
        if (tvReportDate != null) tvReportDate.setText(label);
        List<ExerciseSession> sessions = last7DaysData.get(dateKey);

        int totalSets = 0;
        Set<String> musclesHit = new HashSet<>();
        Map<String, Integer> exerciseReps = new HashMap<>();

        if (sessions != null) {
            for (ExerciseSession s : sessions) {
                totalSets += s.getCompletedSets().size();
                for (WorkoutSet set : s.getCompletedSets()) {
                    String id = set.getExerciseId();
                    exerciseReps.put(id, exerciseReps.getOrDefault(id, 0) + set.getTotalReps());

                    for (WorkoutManager.ExerciseMuscleInfo info : muscleInfoList) {
                        if (info.id.equals(id)) {
                            for (MuscleHeatmapView.MuscleGroup group : info.muscles.keySet()) {
                                musclesHit.add(formatMuscleName(group));
                            }
                        }
                    }
                }
            }
        }

        if (tvHomeTotalSets != null) tvHomeTotalSets.setText(String.valueOf(totalSets));

        if (tvHomeMusclesHit != null) {
            if (musclesHit.isEmpty()) {
                tvHomeMusclesHit.setText("None");
            } else {
                StringBuilder sb = new StringBuilder();
                List<String> sortedMuscles = new ArrayList<>(musclesHit);
                Collections.sort(sortedMuscles);
                for (int i = 0; i < sortedMuscles.size(); i++) {
                    sb.append(sortedMuscles.get(i));
                    if (i < sortedMuscles.size() - 1) sb.append(", ");
                }
                tvHomeMusclesHit.setText(sb.toString());
            }
        }

        if (homeMuscleHeatmap != null) {
            for (MuscleHeatmapView.MuscleGroup group : MuscleHeatmapView.MuscleGroup.values()) {
                homeMuscleHeatmap.setIntensity(group, 0);
            }

            final float MAX_REPS = 24f;
            Map<MuscleHeatmapView.MuscleGroup, Float> groupIntensities = new HashMap<>();

            for (Map.Entry<String, Integer> entry : exerciseReps.entrySet()) {
                String id = entry.getKey();
                int reps = entry.getValue();
                float intensityBase = reps / MAX_REPS;

                for (WorkoutManager.ExerciseMuscleInfo info : muscleInfoList) {
                    if (info.id.equals(id)) {
                        for (Map.Entry<MuscleHeatmapView.MuscleGroup, Float> mEntry : info.muscles.entrySet()) {
                            MuscleHeatmapView.MuscleGroup group = mEntry.getKey();
                            float factor = mEntry.getValue();
                            float current = groupIntensities.getOrDefault(group, 0f);
                            groupIntensities.put(group, current + (intensityBase * factor));
                        }
                    }
                }
            }

            for (Map.Entry<MuscleHeatmapView.MuscleGroup, Float> entry : groupIntensities.entrySet()) {
                homeMuscleHeatmap.setIntensity(entry.getKey(), entry.getValue());
            }
        }
    }

    private String formatMuscleName(MuscleHeatmapView.MuscleGroup group) {
        String name = group.name().toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private void highlightSelectedCircle(LinearLayout selectedDay) {
        if (llStreakContainer == null) return;
        for (int i = 0; i < llStreakContainer.getChildCount(); i++) {
            View v = llStreakContainer.getChildAt(i);
            v.setAlpha(v == selectedDay ? 1.0f : 0.4f);
        }
    }
}
