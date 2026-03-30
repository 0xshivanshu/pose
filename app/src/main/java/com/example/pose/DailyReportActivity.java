package com.example.pose;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

public class DailyReportActivity extends AppCompatActivity {

    private MuscleHeatmapView detailHeatmap;
    private TextView tvDetailTitle, tvMuscleAnalysis;
    private RecyclerView rvExerciseDetails;
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
    private final SimpleDateFormat storageFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_report);

        // Handle Window Insets
        View rootView = findViewById(R.id.daily_report_root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        detailHeatmap = findViewById(R.id.detailHeatmap);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvMuscleAnalysis = findViewById(R.id.tvMuscleAnalysis);
        rvExerciseDetails = findViewById(R.id.rvExerciseDetails);
        ImageButton btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        String dateKey = getIntent().getStringExtra("selected_date");
        if (dateKey != null) {
            try {
                Date d = storageFormat.parse(dateKey);
                tvDetailTitle.setText(displayFormat.format(d));
            } catch (Exception e) {
                tvDetailTitle.setText("Session Details");
            }
            loadData(dateKey);
        }
    }

    private void loadData(String dateKey) {
        List<ExerciseSession> sessions = WorkoutManager.loadSessionsForDate(this, parseDate(dateKey));
        
        Map<String, List<WorkoutSet>> exerciseGroups = new LinkedHashMap<>();
        Map<String, Integer> muscleReps = new HashMap<>();

        for (ExerciseSession session : sessions) {
            for (WorkoutSet set : session.getCompletedSets()) {
                String cat = set.getCategory();
                if (!exerciseGroups.containsKey(cat)) exerciseGroups.put(cat, new ArrayList<>());
                exerciseGroups.get(cat).add(set);
                
                muscleReps.put(cat, muscleReps.getOrDefault(cat, 0) + set.getTotalReps());
            }
        }

        updateHeatmap(muscleReps);
        setupRecyclerView(exerciseGroups);
        updateAnalysis(muscleReps);
    }

    private Date parseDate(String dateStr) {
        try { return storageFormat.parse(dateStr); }
        catch (Exception e) { return new Date(); }
    }

    private void updateHeatmap(Map<String, Integer> muscleReps) {
        final float MAX_REPS = 24f; // Updated to 24
        for (MuscleHeatmapView.MuscleGroup group : MuscleHeatmapView.MuscleGroup.values()) {
            detailHeatmap.setIntensity(group, 0);
        }

        if (muscleReps.containsKey(RepCounter.CAT_BICEP_CURL)) {
            detailHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.BICEPS, muscleReps.get(RepCounter.CAT_BICEP_CURL) / MAX_REPS);
        }
        if (muscleReps.containsKey(RepCounter.CAT_SQUAT)) {
            float intensity = muscleReps.get(RepCounter.CAT_SQUAT) / MAX_REPS;
            detailHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.QUADS, intensity);
            detailHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.GLUTES, intensity * 0.8f);
            detailHeatmap.setIntensity(MuscleHeatmapView.MuscleGroup.HAMSTRINGS, intensity * 0.6f);
        }
    }

    private void updateAnalysis(Map<String, Integer> muscleReps) {
        List<String> muscles = new ArrayList<>();
        if (muscleReps.containsKey(RepCounter.CAT_BICEP_CURL)) muscles.add("Biceps");
        if (muscleReps.containsKey(RepCounter.CAT_SQUAT)) muscles.add("Lower Body");

        if (muscles.isEmpty()) {
            tvMuscleAnalysis.setText("No data recorded");
        } else {
            tvMuscleAnalysis.setText("Primary Focus: " + String.join(" & ", muscles));
        }
    }

    private void setupRecyclerView(Map<String, List<WorkoutSet>> groups) {
        rvExerciseDetails.setLayoutManager(new LinearLayoutManager(this));
        rvExerciseDetails.setAdapter(new ExerciseGroupAdapter(groups));
    }

    private static class ExerciseGroupAdapter extends RecyclerView.Adapter<ExerciseGroupAdapter.ViewHolder> {
        private final List<String> categories;
        private final Map<String, List<WorkoutSet>> groups;

        public ExerciseGroupAdapter(Map<String, List<WorkoutSet>> groups) {
            this.groups = groups;
            this.categories = new ArrayList<>(groups.keySet());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exercise_detail_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String cat = categories.get(position);
            List<WorkoutSet> sets = groups.get(cat);
            holder.tvTitle.setText(cat);
            holder.tvSetsCount.setText(sets.size() + (sets.size() == 1 ? " Set" : " Sets"));
            
            int totalReps = 0;
            for (WorkoutSet s : sets) totalReps += s.getTotalReps();
            holder.tvTotalReps.setText(totalReps + " Reps");

            holder.llSetsContainer.removeAllViews();
            for (int i = 0; i < sets.size(); i++) {
                WorkoutSet set = sets.get(i);
                View setView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.item_set_detail_row, holder.llSetsContainer, false);
                ((TextView) setView.findViewById(R.id.tvSetNum)).setText("SET " + (i + 1));
                
                String repDetails;
                if (cat.equals(RepCounter.CAT_BICEP_CURL)) {
                    repDetails = "L:" + set.getLeftReps() + " R:" + set.getRightReps();
                } else {
                    repDetails = String.valueOf(set.getTotalReps());
                }
                ((TextView) setView.findViewById(R.id.tvRepCount)).setText(repDetails);
                ((TextView) setView.findViewById(R.id.tvDuration)).setText(set.getDurationMillis() / 1000 + "s");
                
                holder.llSetsContainer.addView(setView);
            }
        }

        @Override
        public int getItemCount() { return categories.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSetsCount, tvTotalReps;
            LinearLayout llSetsContainer;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvExTitle);
                tvSetsCount = v.findViewById(R.id.tvExSets);
                tvTotalReps = v.findViewById(R.id.tvExReps);
                llSetsContainer = v.findViewById(R.id.llSetsContainer);
            }
        }
    }
}
