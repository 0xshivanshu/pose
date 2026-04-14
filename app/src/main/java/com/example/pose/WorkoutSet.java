package com.example.pose;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class WorkoutSet implements Serializable {
    private String exerciseId;
    private String category;
    private Map<String, Integer> exerciseCounts;
    private long durationMillis;

    // No-argument constructor for Firebase
    public WorkoutSet() {
        this.exerciseCounts = new HashMap<>();
    }

    public WorkoutSet(String exerciseId, String category, Map<String, Integer> exerciseCounts, long durationMillis) {
        this.exerciseId = exerciseId;
        this.category = category;
        this.exerciseCounts = new HashMap<>(exerciseCounts);
        this.durationMillis = durationMillis;
    }

    public String getExerciseId() {
        return exerciseId;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Integer> getExerciseCounts() {
        return exerciseCounts;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public int getLeftReps() {
        for (Map.Entry<String, Integer> entry : exerciseCounts.entrySet()) {
            if (entry.getKey().endsWith("(L)")) return entry.getValue();
        }
        return 0;
    }

    public int getRightReps() {
        for (Map.Entry<String, Integer> entry : exerciseCounts.entrySet()) {
            if (entry.getKey().endsWith("(R)")) return entry.getValue();
        }
        return 0;
    }

    public int getTotalReps() {
        int total = 0;
        if (exerciseCounts != null) {
            for (int count : exerciseCounts.values()) {
                total += count;
            }
        }
        return total;
    }
}