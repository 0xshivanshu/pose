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
    private View cvStatsContainer;
    private CalendarView calendarView;
    private final SimpleDateFormat storageFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
    private List<WorkoutManager.ExerciseMuscleInfo> muscleInfoList;

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

        muscleInfoList = WorkoutManager.getExerciseMuscleInfo(this);

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
        Map<String, Integer> exerciseReps = new HashMap<>();

        for (ExerciseSession session : sessions) {
            for (WorkoutSet set : session.getCompletedSets()) {
                String cat = set.getCategory();
                String id = set.getExerciseId();
                if (!exerciseGroups.containsKey(cat)) exerciseGroups.put(cat, new ArrayList<>());
                exerciseGroups.get(cat).add(set);
                exerciseReps.put(id, exerciseReps.getOrDefault(id, 0) + set.getTotalReps());
            }
        }

        updateHeatmap(exerciseReps);
        rvExerciseDetails.setAdapter(new ExerciseGroupAdapter(exerciseGroups));
        updateAnalysis(exerciseReps);
    }

    private Date parseDate(String dateStr) {
        try { return storageFormat.parse(dateStr); }
        catch (Exception e) { return new Date(); }
    }

    private void updateHeatmap(Map<String, Integer> exerciseReps) {
        final float MAX_REPS = 24f;
        for (MuscleHeatmapView.MuscleGroup group : MuscleHeatmapView.MuscleGroup.values()) {
            detailHeatmap.setIntensity(group, 0);
        }

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
            detailHeatmap.setIntensity(entry.getKey(), entry.getValue());
        }
    }

    private void updateAnalysis(Map<String, Integer> exerciseReps) {
        Set<String> muscles = new HashSet<>();
        for (String id : exerciseReps.keySet()) {
            for (WorkoutManager.ExerciseMuscleInfo info : muscleInfoList) {
                if (info.id.equals(id)) {
                    for (MuscleHeatmapView.MuscleGroup group : info.muscles.keySet()) {
                        muscles.add(formatMuscleName(group));
                    }
                }
            }
        }

        List<String> sortedMuscles = new ArrayList<>(muscles);
        Collections.sort(sortedMuscles);

        // Compatible way to join strings
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sortedMuscles.size(); i++) {
            sb.append(sortedMuscles.get(i));
            if (i < sortedMuscles.size() - 1) sb.append(" & ");
        }
        String result = sb.toString();
        tvMuscleAnalysis.setText("Primary Focus: " + (result.isEmpty() ? "None" : result));
    }

    private String formatMuscleName(MuscleHeatmapView.MuscleGroup group) {
        String name = group.name().toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
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
                
                String repDetails;
                if (set.getLeftReps() > 0 || set.getRightReps() > 0) {
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
