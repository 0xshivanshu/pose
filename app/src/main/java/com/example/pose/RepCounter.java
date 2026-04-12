package com.example.pose;

import android.os.SystemClock;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepCounter {
    private enum State { IDLE, DOWN, UP }

    private final Map<String, Integer> totalCounts = new HashMap<>();
    private final Map<String, State> states = new HashMap<>();
    private final List<WorkoutSet> completedSets = new ArrayList<>();
    
    private final Map<String, Integer> currentSetExerciseCounts = new HashMap<>();
    private final Map<String, Long> lastRepTimes = new HashMap<>();
    
    private final Map<String, Double> smoothedAngles = new HashMap<>();
    private static final float SMOOTHING_ALPHA = 0.45f;
    private static final long MIN_REP_INTERVAL_MS = 800;

    private String activeExerciseCategory = ""; 
    private int currentSetTotalReps = 0;
    private String lastDetectedExercise = "";
    private int totalReps = 0;
    private boolean isResting = false;
    private long resumeTime = 0;
    private static final long RESUME_COOLDOWN_MS = 1000;
    
    private long setStartTime = 0;

    private RepListener repListener;
    private FormListener formListener;
    private SetListener setListener;
    
    public static final String BICEP_CURL_LEFT = "Left Bicep Curl";
    public static final String BICEP_CURL_RIGHT = "Right Bicep Curl";
    public static final String SQUAT = "Squat";
    
    public static final String CAT_BICEP_CURL = "Bicep Curl";
    public static final String CAT_SQUAT = "Squat";

    public interface RepListener {
        void onRepCompleted(String exercise, int count);
    }

    public interface FormListener {
        void onFormFeedback(String feedback);
    }
    
    public interface SetListener {
        void onSetStarted(String category);
        void onSetFinished(WorkoutSet set);
    }

    public void setRepListener(RepListener listener) {
        this.repListener = listener;
    }

    public void setFormListener(FormListener listener) {
        this.formListener = listener;
    }

    public void setSetListener(SetListener listener) {
        this.setListener = listener;
    }

    public RepCounter() {
        totalCounts.put(BICEP_CURL_LEFT, 0);
        totalCounts.put(BICEP_CURL_RIGHT, 0);
        totalCounts.put(SQUAT, 0);

        states.put(BICEP_CURL_LEFT, State.IDLE);
        states.put(BICEP_CURL_RIGHT, State.IDLE);
        states.put(SQUAT, State.IDLE);
        
        lastRepTimes.put(BICEP_CURL_LEFT, 0L);
        lastRepTimes.put(BICEP_CURL_RIGHT, 0L);
        lastRepTimes.put(SQUAT, 0L);

        resetSetCounts();
    }

    private void resetSetCounts() {
        currentSetExerciseCounts.put(BICEP_CURL_LEFT, 0);
        currentSetExerciseCounts.put(BICEP_CURL_RIGHT, 0);
        currentSetExerciseCounts.put(SQUAT, 0);
        currentSetTotalReps = 0;
    }

    public boolean isResting() {
        return isResting;
    }

    public void setResting(boolean resting) {
        if (this.isResting == resting) return;
        
        this.isResting = resting;
        if (resting) {
            finishCurrentSet();
            resetStates();
        } else {
            resumeTime = SystemClock.elapsedRealtime();
            resetStates();
        }
    }

    private void resetStates() {
        states.put(BICEP_CURL_LEFT, State.IDLE);
        states.put(BICEP_CURL_RIGHT, State.IDLE);
        states.put(SQUAT, State.IDLE);
        lastDetectedExercise = "";
    }

    public void processLandmarks(List<NormalizedLandmark> landmarks) {
        if (isResting || landmarks.size() < 33) return;

        if (SystemClock.elapsedRealtime() - resumeTime < RESUME_COOLDOWN_MS) {
            return;
        }

        // SWAPPED L/R for Mirrored Front Camera
        // MediaPipe 12 (Right side of image) -> Physical Left
        // MediaPipe 11 (Left side of image) -> Physical Right
        if (activeExerciseCategory.isEmpty() || activeExerciseCategory.equals(CAT_BICEP_CURL)) {
            processBicepCurl(landmarks.get(12), landmarks.get(14), landmarks.get(16), landmarks.get(24), BICEP_CURL_LEFT);
            processBicepCurl(landmarks.get(11), landmarks.get(13), landmarks.get(15), landmarks.get(23), BICEP_CURL_RIGHT);
        }
        
        if (activeExerciseCategory.isEmpty() || activeExerciseCategory.equals(CAT_SQUAT)) {
            processSquat(landmarks, SQUAT);
        }
    }

    private String getCategory(String exercise) {
        if (exercise.contains("Bicep Curl")) return CAT_BICEP_CURL;
        if (exercise.equals(SQUAT)) return CAT_SQUAT;
        return "";
    }

    private void startSet(String exercise) {
        activeExerciseCategory = getCategory(exercise);
        resetSetCounts();
        setStartTime = SystemClock.elapsedRealtime();
        if (setListener != null) {
            setListener.onSetStarted(activeExerciseCategory);
        }
    }

    public void finishCurrentSet() {
        if (!activeExerciseCategory.isEmpty() && currentSetTotalReps > 0) {
            long duration = SystemClock.elapsedRealtime() - setStartTime;
            Map<String, Integer> setCounts = new HashMap<>();
            if (activeExerciseCategory.equals(CAT_BICEP_CURL)) {
                setCounts.put(BICEP_CURL_LEFT, currentSetExerciseCounts.get(BICEP_CURL_LEFT));
                setCounts.put(BICEP_CURL_RIGHT, currentSetExerciseCounts.get(BICEP_CURL_RIGHT));
            } else {
                setCounts.put(activeExerciseCategory, currentSetTotalReps);
            }
            
            WorkoutSet set = new WorkoutSet(activeExerciseCategory, setCounts, duration);
            completedSets.add(set);
            if (setListener != null) {
                setListener.onSetFinished(set);
            }
        }
        activeExerciseCategory = "";
        resetSetCounts();
        setStartTime = 0;
    }

    private void processBicepCurl(NormalizedLandmark s, NormalizedLandmark e, NormalizedLandmark w, NormalizedLandmark h, String exercise) {
        float visThreshold = 0.5f;
        if (s.visibility().orElse(0f) < visThreshold || e.visibility().orElse(0f) < visThreshold || 
            w.visibility().orElse(0f) < visThreshold || h.visibility().orElse(0f) < visThreshold) return;

        double bodyArmAngle = ExerciseUtils.calculateAngle(h, s, e);
        
        // Form Check: Elbow Flaring
        if (bodyArmAngle > 60) {
            if (!activeExerciseCategory.isEmpty() && formListener != null) {
                formListener.onFormFeedback("Keep elbow closer to body");
            }
        }

        double rawAngle = ExerciseUtils.calculateAngle(s, e, w);
        double angle = smoothedAngles.getOrDefault(exercise, rawAngle);
        angle = angle + SMOOTHING_ALPHA * (rawAngle - angle);
        smoothedAngles.put(exercise, angle);

        State state = states.get(exercise);
        long currentTime = SystemClock.elapsedRealtime();

        if (state == State.IDLE || state == State.UP) {
            if (angle > 160) { 
                states.put(exercise, State.DOWN);
            }
        } else if (state == State.DOWN) {
            if (angle < 45) { 
                if (currentTime - lastRepTimes.get(exercise) > MIN_REP_INTERVAL_MS) {
                    states.put(exercise, State.UP);
                    lastRepTimes.put(exercise, currentTime);
                    incrementCount(exercise);
                }
            } else if (angle < 90 && angle > 45) {
                // Potential feedback for half-reps
                // if (formListener != null) formListener.onFormFeedback("Go higher!");
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

        if (rVis < 0.5f && lVis < 0.5f) return;

        double rawAngle = (rVis > lVis) ? ExerciseUtils.calculateAngle(rHip, rKnee, rAnkle) : ExerciseUtils.calculateAngle(lHip, lKnee, lAnkle);
        double angle = smoothedAngles.getOrDefault(exercise, rawAngle);
        angle = angle + SMOOTHING_ALPHA * (rawAngle - angle);
        smoothedAngles.put(exercise, angle);

        State state = states.get(exercise);
        long currentTime = SystemClock.elapsedRealtime();

        if (state == State.IDLE || state == State.UP) {
            if (angle < 100) { 
                states.put(exercise, State.DOWN);
            }
        } else if (state == State.DOWN) {
            if (angle > 160) { 
                if (currentTime - lastRepTimes.get(exercise) > MIN_REP_INTERVAL_MS) {
                    states.put(exercise, State.UP);
                    lastRepTimes.put(exercise, currentTime);
                    incrementCount(exercise);
                }
            } else if (angle > 120 && angle < 160) {
                // Feedback for not going deep enough?
            }
        }
        
        // Form Check: Back posture (simplified)
        NormalizedLandmark nose = landmarks.get(0);
        float hipAvgY = (rHip.y() + lHip.y()) / 2f;
        if (nose.y() > hipAvgY) {
           if (formListener != null) formListener.onFormFeedback("Keep chest up");
        }
    }

    private void incrementCount(String exercise) {
        if (activeExerciseCategory.isEmpty()) {
            startSet(exercise);
        }
        
        int newTotal = totalCounts.get(exercise) + 1;
        totalCounts.put(exercise, newTotal);
        
        int newSetCount = currentSetExerciseCounts.get(exercise) + 1;
        currentSetExerciseCounts.get(exercise);
        currentSetExerciseCounts.put(exercise, newSetCount);
        
        currentSetTotalReps++;
        totalReps++;
        lastDetectedExercise = exercise;
        
        if (repListener != null) {
            repListener.onRepCompleted(exercise, newSetCount);
        }
    }

    public int getTotalReps() { return totalReps; }
    public int getCurrentSetTotalReps() { return currentSetTotalReps; }
    public Map<String, Integer> getCurrentSetExerciseCounts() { return new HashMap<>(currentSetExerciseCounts); }
    public String getActiveExerciseCategory() { return activeExerciseCategory; }
    public String getLastDetectedExercise() { return lastDetectedExercise; }
    public Map<String, Integer> getTotalCounts() { return new HashMap<>(totalCounts); }
    public List<WorkoutSet> getCompletedSets() { return new ArrayList<>(completedSets); }
}
