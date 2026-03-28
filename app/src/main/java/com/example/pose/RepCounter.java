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
    private boolean isResting = false;
    private RepListener repListener;
    private FormListener formListener;
    
    public static final String BICEP_CURL_LEFT = "Left Bicep Curl";
    public static final String BICEP_CURL_RIGHT = "Right Bicep Curl";
    public static final String SQUAT = "Squat";

    public interface RepListener {
        void onRepCompleted(String exercise, int count);
    }

    public interface FormListener {
        void onFormFeedback(String feedback);
    }

    public void setRepListener(RepListener listener) {
        this.repListener = listener;
    }

    public void setFormListener(FormListener listener) {
        this.formListener = listener;
    }

    public RepCounter() {
        counts.put(BICEP_CURL_LEFT, 0);
        counts.put(BICEP_CURL_RIGHT, 0);
        counts.put(SQUAT, 0);

        states.put(BICEP_CURL_LEFT, State.IDLE);
        states.put(BICEP_CURL_RIGHT, State.IDLE);
        states.put(SQUAT, State.IDLE);
    }

    public boolean isResting() {
        return isResting;
    }

    public void setResting(boolean resting) {
        this.isResting = resting;
        if (resting) {
            // Reset states when going to rest
            states.put(BICEP_CURL_LEFT, State.IDLE);
            states.put(BICEP_CURL_RIGHT, State.IDLE);
            states.put(SQUAT, State.IDLE);
        }
    }

    public void processLandmarks(List<NormalizedLandmark> landmarks) {
        if (isResting || landmarks.size() < 33) return;

        // Swapping indices to correct mirrored view:
        // Physical Right (mirrored Left) uses Landmark indices 11, 13, 15, 23
        processBicepCurl(landmarks.get(11), landmarks.get(13), landmarks.get(15), landmarks.get(23), BICEP_CURL_RIGHT);
        
        // Physical Left (mirrored Right) uses Landmark indices 12, 14, 16, 24
        processBicepCurl(landmarks.get(12), landmarks.get(14), landmarks.get(16), landmarks.get(24), BICEP_CURL_LEFT);
        
        processSquat(landmarks.get(23), landmarks.get(25), landmarks.get(27), SQUAT);
    }

    private void processBicepCurl(NormalizedLandmark s, NormalizedLandmark e, NormalizedLandmark w, NormalizedLandmark h, String exercise) {
        if (s.visibility().orElse(0f) < 0.85f || e.visibility().orElse(0f) < 0.85f || w.visibility().orElse(0f) < 0.85f || h.visibility().orElse(0f) < 0.85f) return;

        // FORM CHECK: Angle between torso (Hip-Shoulder) and upper arm (Shoulder-Elbow)
        double bodyArmAngle = ExerciseUtils.calculateAngle(h, s, e);
        
        // Threshold set to 30 degrees as requested
        if (bodyArmAngle > 30) {
            // Only suggest correction if at least one rep has been completed
            if (counts.get(exercise) > 0 && formListener != null) {
                formListener.onFormFeedback("Bring your arms closer to the body");
            }
            states.put(exercise, State.IDLE);
            return;
        }

        // Additional posture checks to prevent fake reps during other gestures
        float dy = e.y() - s.y();
        boolean isWristAboveShoulder = w.y() < (s.y() - 0.05f);

        if (dy < 0.05f || isWristAboveShoulder) {
            states.put(exercise, State.IDLE);
            return;
        }

        double angle = ExerciseUtils.calculateAngle(s, e, w);
        State state = states.get(exercise);

        if (state == State.IDLE || state == State.UP) {
            if (angle > 165) states.put(exercise, State.DOWN);
        } else if (state == State.DOWN) {
            if (angle < 35) {
                states.put(exercise, State.UP);
                incrementCount(exercise);
            }
        }
    }

    private void processSquat(NormalizedLandmark h, NormalizedLandmark k, NormalizedLandmark a, String exercise) {
        if (h.visibility().orElse(0f) < 0.85f || k.visibility().orElse(0f) < 0.85f || a.visibility().orElse(0f) < 0.85f) return;

        double angle = ExerciseUtils.calculateAngle(h, k, a);
        State state = states.get(exercise);

        if (state == State.IDLE || state == State.UP) {
            if (angle < 85) states.put(exercise, State.DOWN);
        } else if (state == State.DOWN) {
            if (angle > 170) {
                states.put(exercise, State.UP);
                incrementCount(exercise);
            }
        }
    }

    private void incrementCount(String exercise) {
        int newCount = counts.get(exercise) + 1;
        counts.put(exercise, newCount);
        totalReps++;
        lastDetectedExercise = exercise;
        
        if (repListener != null) {
            repListener.onRepCompleted(exercise, newCount);
        }
    }

    public int getTotalReps() { return totalReps; }
    public String getLastDetectedExercise() { return lastDetectedExercise; }
    public Map<String, Integer> getCounts() { return new HashMap<>(counts); }
}
