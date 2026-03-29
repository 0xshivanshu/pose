package com.example.pose;

import android.os.SystemClock;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepCounter {
    private enum State { IDLE, DOWN, UP }

    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, State> states = new HashMap<>();
    private final Map<String, Boolean> repCompletedInSet = new HashMap<>();
    private String lastDetectedExercise = "";
    private int totalReps = 0;
    private boolean isResting = false;
    private long resumeTime = 0;
    private static final long RESUME_COOLDOWN_MS = 1500; // Ignore reps for 1.5s after resuming

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

        repCompletedInSet.put(BICEP_CURL_LEFT, false);
        repCompletedInSet.put(BICEP_CURL_RIGHT, false);
        repCompletedInSet.put(SQUAT, false);
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
        } else {
            // After resting state, go back to "detecting" mode
            resumeTime = SystemClock.elapsedRealtime();
            lastDetectedExercise = "";
            repCompletedInSet.put(BICEP_CURL_LEFT, false);
            repCompletedInSet.put(BICEP_CURL_RIGHT, false);
            repCompletedInSet.put(SQUAT, false);
            
            // Force reset states to IDLE to prevent the "lowering arm" gesture from triggering a rep
            states.put(BICEP_CURL_LEFT, State.IDLE);
            states.put(BICEP_CURL_RIGHT, State.IDLE);
            states.put(SQUAT, State.IDLE);
        }
    }

    public void processLandmarks(List<NormalizedLandmark> landmarks) {
        if (isResting || landmarks.size() < 33) return;

        // Skip processing for a short duration after resume to avoid counting the arm-lowering movement
        if (SystemClock.elapsedRealtime() - resumeTime < RESUME_COOLDOWN_MS) {
            return;
        }

        // Physical Right (mirrored Left) indices: 11, 13, 15, 23
        processBicepCurl(landmarks.get(11), landmarks.get(13), landmarks.get(15), landmarks.get(23), BICEP_CURL_RIGHT);
        
        // Physical Left (mirrored Right) indices: 12, 14, 16, 24
        processBicepCurl(landmarks.get(12), landmarks.get(14), landmarks.get(16), landmarks.get(24), BICEP_CURL_LEFT);
        
        processSquat(landmarks.get(23), landmarks.get(25), landmarks.get(27), SQUAT);
    }

    private void processBicepCurl(NormalizedLandmark s, NormalizedLandmark e, NormalizedLandmark w, NormalizedLandmark h, String exercise) {
        if (s.visibility().orElse(0f) < 0.85f || e.visibility().orElse(0f) < 0.85f || w.visibility().orElse(0f) < 0.85f || h.visibility().orElse(0f) < 0.85f) return;

        double bodyArmAngle = ExerciseUtils.calculateAngle(h, s, e);
        
        // Threshold set to 45 degrees for more leniency
        if (bodyArmAngle > 45) {
            // ONLY suggest correction if at least one rep has been completed IN THE CURRENT SET
            if (repCompletedInSet.getOrDefault(exercise, false) && formListener != null) {
                formListener.onFormFeedback("Bring your arms closer to the body");
            }
            states.put(exercise, State.IDLE);
            return;
        }

        float dy = e.y() - s.y();
        boolean isWristAboveShoulder = w.y() < (s.y() - 0.05f);

        // If elbow is up or wrist is above shoulder, reset state.
        // This is crucial for ignoring gestures.
        if (dy < 0.05f || isWristAboveShoulder) {
            states.put(exercise, State.IDLE);
            return;
        }

        double angle = ExerciseUtils.calculateAngle(s, e, w);
        State state = states.get(exercise);

        if (state == State.IDLE || state == State.UP) {
            if (angle > 160) {
                states.put(exercise, State.DOWN);
                lastDetectedExercise = exercise; 
            }
        } else if (state == State.DOWN) {
            if (angle < 45) { 
                states.put(exercise, State.UP);
                incrementCount(exercise);
                repCompletedInSet.put(exercise, true); 
            }
        }
    }

    private void processSquat(NormalizedLandmark h, NormalizedLandmark k, NormalizedLandmark a, String exercise) {
        if (h.visibility().orElse(0f) < 0.85f || k.visibility().orElse(0f) < 0.85f || a.visibility().orElse(0f) < 0.85f) return;

        double angle = ExerciseUtils.calculateAngle(h, k, a);
        State state = states.get(exercise);

        if (state == State.IDLE || state == State.UP) {
            if (angle < 90) { 
                states.put(exercise, State.DOWN);
                lastDetectedExercise = exercise;
            }
        } else if (state == State.DOWN) {
            if (angle > 160) { 
                states.put(exercise, State.UP);
                incrementCount(exercise);
                repCompletedInSet.put(exercise, true);
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
    
    public int getLastExerciseCount() {
        if (lastDetectedExercise.isEmpty()) return 0;
        return counts.getOrDefault(lastDetectedExercise, 0);
    }
    
    public String getLastDetectedExercise() { return lastDetectedExercise; }
    public Map<String, Integer> getCounts() { return new HashMap<>(counts); }
}
