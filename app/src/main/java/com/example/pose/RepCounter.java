package com.example.pose;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepCounter {
    private enum State { IDLE, DOWN, UP }

    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, State> states = new HashMap<>();
    private String lastDetectedExercise = "";
    private int totalReps = 0;
    
    public static final String BICEP_CURL_LEFT = "Left Bicep Curl";
    public static final String BICEP_CURL_RIGHT = "Right Bicep Curl";
    public static final String SQUAT = "Squat";

    public RepCounter() {
        counts.put(BICEP_CURL_LEFT, 0);
        counts.put(BICEP_CURL_RIGHT, 0);
        counts.put(SQUAT, 0);

        states.put(BICEP_CURL_LEFT, State.IDLE);
        states.put(BICEP_CURL_RIGHT, State.IDLE);
        states.put(SQUAT, State.IDLE);
    }

    public void processLandmarks(List<NormalizedLandmark> landmarks) {
        if (landmarks.size() < 33) return;

        // Swapping landmarks to match mirrored camera view or user preference
        // 11, 13, 15 are Left side in MediaPipe. If they appear as Right in UI, we label them Right.
        processBicepCurl(landmarks.get(11), landmarks.get(13), landmarks.get(15), BICEP_CURL_RIGHT);
        processBicepCurl(landmarks.get(12), landmarks.get(14), landmarks.get(16), BICEP_CURL_LEFT);
        processSquat(landmarks.get(23), landmarks.get(25), landmarks.get(27), SQUAT);
    }

    private void processBicepCurl(NormalizedLandmark s, NormalizedLandmark e, NormalizedLandmark w, String exercise) {
        // Tighter visibility threshold
        if (s.visibility().orElse(0f) < 0.8f || e.visibility().orElse(0f) < 0.8f || w.visibility().orElse(0f) < 0.8f) return;

        double angle = ExerciseUtils.calculateAngle(s, e, w);
        State state = states.get(exercise);

        // Tighter angle constraints
        if (state == State.IDLE || state == State.UP) {
            // Must fully extend arm
            if (angle > 165) states.put(exercise, State.DOWN);
        } else if (state == State.DOWN) {
            // Must fully contract bicep
            if (angle < 35) {
                states.put(exercise, State.UP);
                incrementCount(exercise);
            }
        }
    }

    private void processSquat(NormalizedLandmark h, NormalizedLandmark k, NormalizedLandmark a, String exercise) {
        // Tighter visibility threshold
        if (h.visibility().orElse(0f) < 0.8f || k.visibility().orElse(0f) < 0.8f || a.visibility().orElse(0f) < 0.8f) return;

        double angle = ExerciseUtils.calculateAngle(h, k, a);
        State state = states.get(exercise);

        // Tighter angle constraints for depth and standing straight
        if (state == State.IDLE || state == State.UP) {
            // Go lower for a squat
            if (angle < 90) states.put(exercise, State.DOWN);
        } else if (state == State.DOWN) {
            // Stand up fully
            if (angle > 165) {
                states.put(exercise, State.UP);
                incrementCount(exercise);
            }
        }
    }

    private void incrementCount(String exercise) {
        counts.put(exercise, counts.get(exercise) + 1);
        totalReps++;
        lastDetectedExercise = exercise;
    }

    public int getTotalReps() { return totalReps; }
    public String getLastDetectedExercise() { return lastDetectedExercise; }
    public Map<String, Integer> getCounts() { return new HashMap<>(counts); }
}
