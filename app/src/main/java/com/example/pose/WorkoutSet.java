package com.example.pose;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class WorkoutSet implements Serializable {
    private final String category;
    private final Map<String, Integer> exerciseCounts;
    private final long durationMillis;

    public WorkoutSet(String category, Map<String, Integer> exerciseCounts, long durationMillis) {
        this.category = category;
        this.exerciseCounts = new HashMap<>(exerciseCounts);
        this.durationMillis = durationMillis;
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
        return exerciseCounts.getOrDefault(RepCounter.BICEP_CURL_LEFT, 0);
    }

    public int getRightReps() {
        return exerciseCounts.getOrDefault(RepCounter.BICEP_CURL_RIGHT, 0);
    }

    public int getTotalReps() {
        int total = 0;
        for (int count : exerciseCounts.values()) {
            total += count;
        }
        return total;
    }
}
