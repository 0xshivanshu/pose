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
    private static final long RESUME_COOLDOWN_MS = 1500; 

    private RepListener repListener;
    private FormListener formListener;
    
    public static final String BICEP_CURL_LEFT = "Left Bicep Curl";
    public static final String BICEP_CURL_RIGHT = "Right Bicep Curl";
    public static final String SQUAT = "Squat";
    // public static final String LUNGE = "Lunge";

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
        // counts.put(LUNGE, 0);

        states.put(BICEP_CURL_LEFT, State.IDLE);
        states.put(BICEP_CURL_RIGHT, State.IDLE);
        states.put(SQUAT, State.IDLE);
        // states.put(LUNGE, State.IDLE);

        repCompletedInSet.put(BICEP_CURL_LEFT, false);
        repCompletedInSet.put(BICEP_CURL_RIGHT, false);
        repCompletedInSet.put(SQUAT, false);
        // repCompletedInSet.put(LUNGE, false);
    }

    public boolean isResting() {
        return isResting;
    }

    public void setResting(boolean resting) {
        this.isResting = resting;
        if (resting) {
            states.put(BICEP_CURL_LEFT, State.IDLE);
            states.put(BICEP_CURL_RIGHT, State.IDLE);
            states.put(SQUAT, State.IDLE);
            // states.put(LUNGE, State.IDLE);
        } else {
            resumeTime = SystemClock.elapsedRealtime();
            lastDetectedExercise = "";
            repCompletedInSet.put(BICEP_CURL_LEFT, false);
            repCompletedInSet.put(BICEP_CURL_RIGHT, false);
            repCompletedInSet.put(SQUAT, false);
            // repCompletedInSet.put(LUNGE, false);
            
            states.put(BICEP_CURL_LEFT, State.IDLE);
            states.put(BICEP_CURL_RIGHT, State.IDLE);
            states.put(SQUAT, State.IDLE);
            // states.put(LUNGE, State.IDLE);
        }
    }

    public void processLandmarks(List<NormalizedLandmark> landmarks) {
        if (isResting || landmarks.size() < 33) return;

        if (SystemClock.elapsedRealtime() - resumeTime < RESUME_COOLDOWN_MS) {
            return;
        }

        processBicepCurl(landmarks.get(11), landmarks.get(13), landmarks.get(15), landmarks.get(23), BICEP_CURL_RIGHT);
        processBicepCurl(landmarks.get(12), landmarks.get(14), landmarks.get(16), landmarks.get(24), BICEP_CURL_LEFT);
        processSquat(landmarks, SQUAT);
        // processLunge(landmarks, LUNGE);
    }

    private void processBicepCurl(NormalizedLandmark s, NormalizedLandmark e, NormalizedLandmark w, NormalizedLandmark h, String exercise) {
        if (s.visibility().orElse(0f) < 0.85f || e.visibility().orElse(0f) < 0.85f || w.visibility().orElse(0f) < 0.85f || h.visibility().orElse(0f) < 0.85f) return;

        double bodyArmAngle = ExerciseUtils.calculateAngle(h, s, e);
        
        if (bodyArmAngle > 45) {
            if (repCompletedInSet.getOrDefault(exercise, false) && formListener != null) {
                formListener.onFormFeedback("Bring your arms closer to the body");
            }
            states.put(exercise, State.IDLE);
            return;
        }

        if (w.y() < s.y()) {
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

    private void processSquat(List<NormalizedLandmark> landmarks, String exercise) {
        NormalizedLandmark rHip = landmarks.get(23); 
        NormalizedLandmark rKnee = landmarks.get(25);
        NormalizedLandmark rAnkle = landmarks.get(27);
        NormalizedLandmark lHip = landmarks.get(24); 
        NormalizedLandmark lKnee = landmarks.get(26);
        NormalizedLandmark lAnkle = landmarks.get(28);

        float rVis = (rHip.visibility().orElse(0f) + rKnee.visibility().orElse(0f) + rAnkle.visibility().orElse(0f)) / 3f;
        float lVis = (lHip.visibility().orElse(0f) + lKnee.visibility().orElse(0f) + lAnkle.visibility().orElse(0f)) / 3f;

        if (rVis < 0.7f && lVis < 0.7f) return;

        double angle = (rVis > lVis) ? ExerciseUtils.calculateAngle(rHip, rKnee, rAnkle) : ExerciseUtils.calculateAngle(lHip, lKnee, lAnkle);
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

    /*
    private void processLunge(List<NormalizedLandmark> landmarks, String exercise) {
        NormalizedLandmark rHip = landmarks.get(23);
        NormalizedLandmark rKnee = landmarks.get(25);
        NormalizedLandmark rAnkle = landmarks.get(27);
        NormalizedLandmark lHip = landmarks.get(24);
        NormalizedLandmark lKnee = landmarks.get(26);
        NormalizedLandmark lAnkle = landmarks.get(28);

        double rAngle = ExerciseUtils.calculateAngle(rHip, rKnee, rAnkle);
        double lAngle = ExerciseUtils.calculateAngle(lHip, lKnee, lAnkle);

        State state = states.get(exercise);

        if (state == State.IDLE || state == State.UP) {
            if (rAngle < 100 || lAngle < 100) {
                states.put(exercise, State.DOWN);
                lastDetectedExercise = exercise;
            }
        } else if (state == State.DOWN) {
            if (rAngle > 150 && lAngle > 150) {
                states.put(exercise, State.UP);
                incrementCount(exercise);
                repCompletedInSet.put(exercise, true);
            }
        }
    }
    */

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
