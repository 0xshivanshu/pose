package com.example.pose;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class SummaryActivity extends AppCompatActivity {
    private static final String TAG = "SummaryActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        try {
            setupWindowInsets();
            initializeUI();
        } catch (Exception e) {
            Log.e(TAG, "Error during SummaryActivity initialization", e);
            // If it crashes during init, at least show the basic layout
        }
    }

    private void setupWindowInsets() {
        View rootView = findViewById(R.id.summary_root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
    }

    private void initializeUI() {
        List<WorkoutManager.ExerciseMuscleInfo> muscleInfoList = WorkoutManager.getExerciseMuscleInfo(this);
        ExerciseSession session = (ExerciseSession) getIntent().getSerializableExtra("EXERCISE_SESSION");

        TextView tvTotalSets = findViewById(R.id.tvTotalSets);
        TextView tvDuration = findViewById(R.id.tvDuration);
        LinearLayout llBreakdownContainer = findViewById(R.id.llBreakdownContainer);
        Button btnRestart = findViewById(R.id.btnRestart);
        Button btnHome = findViewById(R.id.btnHome);
        MuscleHeatmapView heatmapView = findViewById(R.id.muscleHeatmap);

        if (session == null) {
            Log.w(TAG, "No session data found in intent");
            if (tvTotalSets != null) tvTotalSets.setText("0");
            if (tvDuration != null) tvDuration.setText("0s");
            return;
        }

        // 1. Core Stats
        if (tvTotalSets != null) {
            int setSize = session.getCompletedSets() != null ? session.getCompletedSets().size() : 0;
            tvTotalSets.setText(String.valueOf(setSize));
        }
        
        if (tvDuration != null) {
            tvDuration.setText(formatDuration(session.getDurationSeconds()));
        }

        // 2. Data Processing
        List<WorkoutSet> sets = session.getCompletedSets();
        Map<String, List<WorkoutSet>> groupedSets = new HashMap<>();
        Map<String, Integer> exerciseReps = new HashMap<>();

        if (sets != null) {
            for (WorkoutSet set : sets) {
                if (set == null) continue;
                String cat = set.getCategory();
                String id = set.getExerciseId();
                if (cat == null) cat = "Unknown";
                
                if (!groupedSets.containsKey(cat)) {
                    groupedSets.put(cat, new ArrayList<>());
                }
                groupedSets.get(cat).add(set);
                exerciseReps.put(id, exerciseReps.getOrDefault(id, 0) + set.getTotalReps());
            }
        }

        // 3. Heatmap
        if (heatmapView != null) {
            updateHeatmap(heatmapView, exerciseReps, muscleInfoList);
        }

        // 4. Breakdown List
        if (llBreakdownContainer != null) {
            llBreakdownContainer.removeAllViews();
            if (groupedSets.isEmpty()) {
                addEmptyStateView(llBreakdownContainer);
            } else {
                populateBreakdown(llBreakdownContainer, groupedSets);
            }
        }

        // 5. Navigation
        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }

        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private String formatDuration(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + "s";
        long mins = totalSeconds / 60;
        long secs = totalSeconds % 60;
        return String.format(Locale.US, "%dm %ds", mins, secs);
    }

    private void addEmptyStateView(LinearLayout container) {
        TextView tvNoData = new TextView(this);
        tvNoData.setText("No exercises recorded in this session.");
        tvNoData.setTextColor(Color.parseColor("#9CA3AF"));
        tvNoData.setPadding(0, 48, 0, 0);
        tvNoData.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        container.addView(tvNoData);
    }

    private void populateBreakdown(LinearLayout container, Map<String, List<WorkoutSet>> groupedSets) {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, List<WorkoutSet>> entry : groupedSets.entrySet()) {
            String category = entry.getKey();
            List<WorkoutSet> categorySets = entry.getValue();
            
            View exerciseView = inflater.inflate(R.layout.item_exercise_summary, container, false);
            TextView tvName = exerciseView.findViewById(R.id.tvExerciseName);
            TextView tvCount = exerciseView.findViewById(R.id.tvExerciseCount);
            TextView tvSetCount = exerciseView.findViewById(R.id.tvSetCount);
            LinearLayout llSetsContainer = exerciseView.findViewById(R.id.llSetsContainer);
            View llHeader = exerciseView.findViewById(R.id.llHeader);
            ImageView ivExpandArrow = exerciseView.findViewById(R.id.ivExpandArrow);
            
            int totalReps = 0;
            for (WorkoutSet s : categorySets) totalReps += s.getTotalReps();
            
            if (tvName != null) tvName.setText(category);
            if (tvSetCount != null) tvSetCount.setText(categorySets.size() + (categorySets.size() == 1 ? " set" : " sets"));
            if (tvCount != null) tvCount.setText(String.valueOf(totalReps));
            
            if (llHeader != null && llSetsContainer != null) {
                llHeader.setOnClickListener(v -> {
                    boolean isVisible = llSetsContainer.getVisibility() == View.VISIBLE;
                    llSetsContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                    if (ivExpandArrow != null) ivExpandArrow.setRotation(isVisible ? 0 : 180);
                });
            }

            for (int i = 0; i < categorySets.size(); i++) {
                WorkoutSet set = categorySets.get(i);
                View setView = inflater.inflate(R.layout.item_set_summary, llSetsContainer, false);
                TextView tvSetName = setView.findViewById(R.id.tvSetName);
                TextView tvSetReps = setView.findViewById(R.id.tvSetReps);
                
                if (tvSetName != null) {
                    long d = set.getDurationMillis() / 1000;
                    tvSetName.setText(String.format(Locale.US, "Set %d (%ds)", (i + 1), d));
                }
                
                if (tvSetReps != null) {
                    if (set.getLeftReps() > 0 || set.getRightReps() > 0) {
                        tvSetReps.setText("L " + set.getLeftReps() + "  R " + set.getRightReps());
                    } else {
                        tvSetReps.setText(String.valueOf(set.getTotalReps()));
                    }
                }
                llSetsContainer.addView(setView);
            }
            container.addView(exerciseView);
        }
    }

    private void updateHeatmap(MuscleHeatmapView heatmapView, Map<String, Integer> exerciseReps, List<WorkoutManager.ExerciseMuscleInfo> muscleInfoList) {
        final float MAX_REPS = 20f;
        for (MuscleHeatmapView.MuscleGroup group : MuscleHeatmapView.MuscleGroup.values()) {
            heatmapView.setIntensity(group, 0);
        }

        Map<MuscleHeatmapView.MuscleGroup, Float> groupIntensities = new HashMap<>();
        for (Map.Entry<String, Integer> entry : exerciseReps.entrySet()) {
            String id = entry.getKey();
            float intensityBase = entry.getValue() / MAX_REPS;

            for (WorkoutManager.ExerciseMuscleInfo info : muscleInfoList) {
                if (info.id.equals(id) && info.muscles != null) {
                    for (Map.Entry<MuscleHeatmapView.MuscleGroup, Float> mEntry : info.muscles.entrySet()) {
                        float current = groupIntensities.getOrDefault(mEntry.getKey(), 0f);
                        groupIntensities.put(mEntry.getKey(), current + (intensityBase * mEntry.getValue()));
                    }
                }
            }
        }

        for (Map.Entry<MuscleHeatmapView.MuscleGroup, Float> entry : groupIntensities.entrySet()) {
            heatmapView.setIntensity(entry.getKey(), entry.getValue());
        }
    }
}
