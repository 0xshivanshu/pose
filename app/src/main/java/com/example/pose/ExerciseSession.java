package com.example.pose;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class ExerciseSession implements Serializable {
    private final Map<String, Integer> totalExerciseCounts;
    private final List<WorkoutSet> completedSets;
    private final long durationSeconds;

    public ExerciseSession(Map<String, Integer> totalExerciseCounts, List<WorkoutSet> completedSets, long durationSeconds) {
        this.totalExerciseCounts = totalExerciseCounts;
        this.completedSets = completedSets;
        this.durationSeconds = durationSeconds;
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

    public int getTotalReps() {
        int total = 0;
        for (int count : totalExerciseCounts.values()) {
            total += count;
        }
        return total;
    }
}
