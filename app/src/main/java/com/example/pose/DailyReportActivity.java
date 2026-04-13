package com.example.pose;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
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
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.*;

public class DailyReportActivity extends AppCompatActivity {

    private MuscleHeatmapView detailHeatmap;
    private TextView tvDetailTitle, tvMuscleAnalysis, tvBreakdownLabel;
    private RecyclerView rvExerciseDetails;
    private MaterialCardView cvStatsContainer;
    private CalendarView calendarView;
    private final SimpleDateFormat storageFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_report);

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
        tvBreakdownLabel = findViewById(R.id.tvBreakdownLabel);
        rvExerciseDetails = findViewById(R.id.rvExerciseDetails);
        cvStatsContainer = findViewById(R.id.cvStatsContainer);
        calendarView = findViewById(R.id.calendarView);
        ImageButton btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        rvExerciseDetails.setLayoutManager(new LinearLayoutManager(this));

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, dayOfMonth);
            String selectedDate = storageFormat.format(cal.getTime());
            tvDetailTitle.setText(displayFormat.format(cal.getTime()));
            loadData(selectedDate);
        });

        // Initialize with today or intent date
        String initialDate = getIntent().getStringExtra("selected_date");
        if (initialDate == null) initialDate = storageFormat.format(new Date());
        
        try {
            Date d = storageFormat.parse(initialDate);
            if (d != null) {
                calendarView.setDate(d.getTime());
                tvDetailTitle.setText(displayFormat.format(d));
            }
        } catch (Exception ignored) {}
        
        loadData(initialDate);
    }

    private void loadData(String dateKey) {
        List<ExerciseSession> sessions = WorkoutManager.loadSessionsForDate(this, parseDate(dateKey));
        
        if (sessions == null || sessions.isEmpty()) {
            cvStatsContainer.setVisibility(View.GONE);
            tvBreakdownLabel.setVisibility(View.GONE);
            rvExerciseDetails.setVisibility(View.GONE);
            tvMuscleAnalysis.setText("No workout recorded for this day.");
            return;
        }

        cvStatsContainer.setVisibility(View.VISIBLE);
        tvBreakdownLabel.setVisibility(View.VISIBLE);
        rvExerciseDetails.setVisibility(View.VISIBLE);

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
        rvExerciseDetails.setAdapter(new ExerciseGroupAdapter(exerciseGroups));
        updateAnalysis(muscleReps);
    }

    private Date parseDate(String dateStr) {
        try { return storageFormat.parse(dateStr); }
        catch (Exception e) { return new Date(); }
    }

    private void updateHeatmap(Map<String, Integer> muscleReps) {
        final float MAX_REPS = 24f;
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
        tvMuscleAnalysis.setText("Primary Focus: " + (muscles.isEmpty() ? "None" : String.join(" & ", muscles)));
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
            holder.tvTotalReps.setText(totalReps + " Total Reps");

            holder.llSetsContainer.removeAllViews();
            for (int i = 0; i < sets.size(); i++) {
                WorkoutSet set = sets.get(i);
                View setView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.item_set_detail_row, holder.llSetsContainer, false);
                ((TextView) setView.findViewById(R.id.tvSetNum)).setText("SET " + (i + 1));
                String repDetails = cat.equals(RepCounter.CAT_BICEP_CURL) ? "L:" + set.getLeftReps() + " R:" + set.getRightReps() : String.valueOf(set.getTotalReps());
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
