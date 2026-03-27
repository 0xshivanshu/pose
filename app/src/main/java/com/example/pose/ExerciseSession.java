package com.example.pose;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ExerciseSession implements Serializable {
    private final Map<String, Integer> exerciseCounts;
    private final long durationSeconds;

    public ExerciseSession(Map<String, Integer> exerciseCounts, long durationSeconds) {
        this.exerciseCounts = exerciseCounts;
        this.durationSeconds = durationSeconds;
    }

    public Map<String, Integer> getExerciseCounts() {
        return exerciseCounts;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public int getTotalReps() {
        int total = 0;
        for (int count : exerciseCounts.values()) {
            total += count;
        }
        return total;
    }
}
