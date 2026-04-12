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
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.*;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout llStreakContainer;
    private TextView tvReportDate, tvHomeTotalSets, tvHomeMusclesHit, tvStreakCount, tvStreakMessage;
    private ProgressBar pbStreak;
    private MuscleHeatmapView homeMuscleHeatmap;
    private MaterialCardView cvDailyReport;
    private Map<String, List<ExerciseSession>> last7DaysData;
    private final SimpleDateFormat dayNameFormat = new SimpleDateFormat("EEE", Locale.US);
    private final SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private String selectedDateKey;
    private FirebaseAuth mAuth;

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

        cvDailyReport.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, DailyReportActivity.class);
            intent.putExtra("selected_date", selectedDateKey);
            startActivity(intent);
        });

        Button btnStartWorkout = findViewById(R.id.btnStartWorkout);
        btnStartWorkout.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(intent);
        });

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
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

        loadAndDisplayData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() != null) {
            loadAndDisplayData();
        }
    }

    private void loadAndDisplayData() {
        last7DaysData = WorkoutManager.getLast7DaysSessions(this);
        
        // Update Streak Info
        int currentStreak = WorkoutManager.calculateCurrentStreak(this);
        tvStreakCount.setText(currentStreak + " Day Streak");
        pbStreak.setProgress(Math.min(7, currentStreak));
        
        if (currentStreak == 0) {
            tvStreakMessage.setText("Start your first session!");
        } else if (currentStreak < 3) {
            tvStreakMessage.setText("Great start! Keep it up.");
        } else if (currentStreak < 7) {
            tvStreakMessage.setText("You're on fire!");
        } else {
            tvStreakMessage.setText("Weekly goal achieved!");
        }

        renderStreakCircles();
        
        // Default to today
        String today = fullDateFormat.format(new Date());
        displayDayReport(today, "Today's Report");
    }

    private void renderStreakCircles() {
        llStreakContainer.removeAllViews();
        String todayStr = fullDateFormat.format(new Date());
        
        // Get the dates and sort them to ensure chronological order
        List<String> sortedDates = new ArrayList<>(last7DaysData.keySet());
        Collections.sort(sortedDates);
        
        for (String dateKey : sortedDates) {
            List<ExerciseSession> sessions = last7DaysData.get(dateKey);
            boolean hasWorkout = !sessions.isEmpty();
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
        tvReportDate.setText(label);
        List<ExerciseSession> sessions = last7DaysData.get(dateKey);
        
        int totalSets = 0;
        Set<String> musclesHit = new HashSet<>();
        Map<String, Integer> muscleReps = new HashMap<>();

        if (sessions != null) {
            for (ExerciseSession s : sessions) {
                totalSets += s.getCompletedSets().size();
                for (WorkoutSet set : s.getCompletedSets()) {
                    String cat = set.getCategory();
                    muscleReps.put(cat, muscleReps.getOrDefault(cat, 0) + set.getTotalReps());
                    
                    if (cat.equals(RepCounter.CAT_BICEP_CURL)) musclesHit.add("Biceps");
                    if (cat.equals(RepCounter.CAT_SQUAT)) {
                        musclesHit.add("Quads");
                        musclesHit.add("Glutes");
                    }
                }
            }
        }

        tvHomeTotalSets.setText(String.valueOf(totalSets));
        
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
        
        // Reset Heatmap
        for (MuscleHeatmapView.MuscleGroup group : MuscleHeatmapView.MuscleGroup.values()) {
            homeMuscleHeatmap.setIntensity(group, 0);
        }

        final float MAX_REPS = 24f; // Updated to 24
        if (muscleReps.containsKey(RepCounter.CAT_BICEP_CURL)) {
            homeMuscleHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.BICEPS, muscleReps.get(RepCounter.CAT_BICEP_CURL) / MAX_REPS);
        }
        if (muscleReps.containsKey(RepCounter.CAT_SQUAT)) {
            float intensity = muscleReps.get(RepCounter.CAT_SQUAT) / MAX_REPS;
            homeMuscleHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.QUADS, intensity);
            homeMuscleHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.GLUTES, intensity * 0.8f);
            homeMuscleHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.HAMSTRINGS, intensity * 0.6f);
        }
    }

    private void highlightSelectedCircle(LinearLayout selectedDay) {
        for (int i = 0; i < llStreakContainer.getChildCount(); i++) {
            View v = llStreakContainer.getChildAt(i);
            v.setAlpha(v == selectedDay ? 1.0f : 0.4f);
        }
    }
}
