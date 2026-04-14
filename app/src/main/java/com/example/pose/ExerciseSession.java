package com.example.pose;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExerciseSession implements Serializable {
    private Map<String, Integer> totalExerciseCounts;
    private List<WorkoutSet> completedSets;
    private long durationSeconds;
    private long timestamp;

    // No-argument constructor for Firebase
    public ExerciseSession() {
        this.timestamp = System.currentTimeMillis();
    }

    public ExerciseSession(Map<String, Integer> totalExerciseCounts, List<WorkoutSet> completedSets, long durationSeconds) {
        this.totalExerciseCounts = totalExerciseCounts != null ? new HashMap<>(totalExerciseCounts) : new HashMap<>();
        this.completedSets = completedSets;
        this.durationSeconds = durationSeconds;
        this.timestamp = System.currentTimeMillis();
    }

    public Map<String, Integer> getTotalExerciseCounts() {
        return totalExerciseCounts;
    }

    public List<WorkoutSet> getCompletedSets() {
        return completedSets;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getTotalReps() {
        int total = 0;
        if (totalExerciseCounts != null) {
            for (int count : totalExerciseCounts.values()) {
                total += count;
            }
        }
        return total;
    }
}