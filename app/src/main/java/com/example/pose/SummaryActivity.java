package com.example.pose;

import android.content.Intent;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        // Handle Window Insets
        View rootView = findViewById(R.id.summary_root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        ExerciseSession session = (ExerciseSession) getIntent().getSerializableExtra("EXERCISE_SESSION");
        
        TextView tvTotalSets = findViewById(R.id.tvTotalSets);
        TextView tvDuration = findViewById(R.id.tvDuration);
        LinearLayout llBreakdownContainer = findViewById(R.id.llBreakdownContainer);
        Button btnRestart = findViewById(R.id.btnRestart);
        Button btnHome = findViewById(R.id.btnHome);
        MuscleHeatmapView heatmapView = findViewById(R.id.muscleHeatmap);

        if (session != null) {
            tvTotalSets.setText(String.valueOf(session.getCompletedSets().size()));
            tvDuration.setText(session.getDurationSeconds() + "s");

            List<WorkoutSet> sets = session.getCompletedSets();
            
            // Group sets by exercise category and calculate muscle intensities
            Map<String, List<WorkoutSet>> groupedSets = new HashMap<>();
            Map<String, Integer> totalExerciseReps = new HashMap<>();

            for (WorkoutSet set : sets) {
                String category = set.getCategory();
                if (!groupedSets.containsKey(category)) {
                    groupedSets.put(category, new ArrayList<>());
                }
                groupedSets.get(category).add(set);
                
                // Track total reps per exercise type for heatmap
                if (category.equals(RepCounter.CAT_BICEP_CURL)) {
                    totalExerciseReps.put(RepCounter.CAT_BICEP_CURL, 
                        totalExerciseReps.getOrDefault(RepCounter.CAT_BICEP_CURL, 0) + set.getTotalReps());
                } else if (category.equals(RepCounter.CAT_SQUAT)) {
                    totalExerciseReps.put(RepCounter.CAT_SQUAT, 
                        totalExerciseReps.getOrDefault(RepCounter.CAT_SQUAT, 0) + set.getTotalReps());
                }
            }

            updateHeatmap(heatmapView, totalExerciseReps);

            LayoutInflater inflater = LayoutInflater.from(this);
            
            if (groupedSets.isEmpty()) {
                TextView tvNoData = new TextView(this);
                tvNoData.setText("No exercises detected.");
                tvNoData.setTextColor(android.graphics.Color.WHITE);
                tvNoData.setPadding(0, 32, 0, 0);
                tvNoData.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                llBreakdownContainer.addView(tvNoData);
            } else {
                for (Map.Entry<String, List<WorkoutSet>> entry : groupedSets.entrySet()) {
                    String category = entry.getKey();
                    List<WorkoutSet> categorySets = entry.getValue();
                    
                    // Main Exercise Card
                    View exerciseView = inflater.inflate(R.layout.item_exercise_summary, llBreakdownContainer, false);
                    TextView tvName = exerciseView.findViewById(R.id.tvExerciseName);
                    TextView tvCount = exerciseView.findViewById(R.id.tvExerciseCount);
                    TextView tvSetCount = exerciseView.findViewById(R.id.tvSetCount);
                    LinearLayout llSetsContainer = exerciseView.findViewById(R.id.llSetsContainer);
                    LinearLayout llHeader = exerciseView.findViewById(R.id.llHeader);
                    ImageView ivExpandArrow = exerciseView.findViewById(R.id.ivExpandArrow);
                    
                    int totalCategoryReps = 0;
                    for (WorkoutSet s : categorySets) totalCategoryReps += s.getTotalReps();
                    
                    tvName.setText(category);
                    tvSetCount.setText(categorySets.size() + (categorySets.size() == 1 ? " set" : " sets"));
                    tvCount.setText(String.valueOf(totalCategoryReps));
                    
                    // Setup dropdown logic
                    llHeader.setOnClickListener(v -> {
                        if (llSetsContainer.getVisibility() == View.VISIBLE) {
                            llSetsContainer.setVisibility(View.GONE);
                            ivExpandArrow.setRotation(0);
                        } else {
                            llSetsContainer.setVisibility(View.VISIBLE);
                            ivExpandArrow.setRotation(180);
                        }
                    });

                    // Add individual sets to the sets container
                    for (int i = 0; i < categorySets.size(); i++) {
                        WorkoutSet set = categorySets.get(i);
                        View setView = inflater.inflate(R.layout.item_set_summary, llSetsContainer, false);
                        TextView tvSetName = setView.findViewById(R.id.tvSetName);
                        TextView tvSetReps = setView.findViewById(R.id.tvSetReps);
                        
                        long durationSec = set.getDurationMillis() / 1000;
                        tvSetName.setText(String.format(Locale.US, "Set %d (%ds)", (i + 1), durationSec));
                        
                        if (category.equals(RepCounter.CAT_BICEP_CURL)) {
                            Map<String, Integer> counts = set.getExerciseCounts();
                            int left = counts.getOrDefault(RepCounter.BICEP_CURL_LEFT, 0);
                            int right = counts.getOrDefault(RepCounter.BICEP_CURL_RIGHT, 0);
                            tvSetReps.setText("L: " + left + " R: " + right);
                        } else {
                            tvSetReps.setText(String.valueOf(set.getTotalReps()));
                        }
                        
                        llSetsContainer.addView(setView);
                    }
                    
                    llBreakdownContainer.addView(exerciseView);
                }
            }
        }

        btnRestart.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void updateHeatmap(MuscleHeatmapView heatmapView, Map<String, Integer> totalExerciseReps) {
        // Target reps for 100% intensity - updated to 36 as requested
        final float MAX_REPS = 24f;

        if (totalExerciseReps.containsKey(RepCounter.CAT_BICEP_CURL)) {
            float intensity = totalExerciseReps.get(RepCounter.CAT_BICEP_CURL) / MAX_REPS;
            heatmapView.setIntensity(MuscleHeatmapView.MuscleGroup.BICEPS, intensity);
        }

        if (totalExerciseReps.containsKey(RepCounter.CAT_SQUAT)) {
            float intensity = totalExerciseReps.get(RepCounter.CAT_SQUAT) / MAX_REPS;
            // Squats work multiple muscles
            heatmapView.setIntensity(MuscleHeatmapView.MuscleGroup.QUADS, intensity);
            heatmapView.setIntensity(MuscleHeatmapView.MuscleGroup.GLUTES, intensity * 0.8f);
            heatmapView.setIntensity(MuscleHeatmapView.MuscleGroup.HAMSTRINGS, intensity * 0.6f);
        }
    }
}
