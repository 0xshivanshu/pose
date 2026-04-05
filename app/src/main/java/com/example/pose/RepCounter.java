package com.example.pose;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepCounter {
    private final Map<String, Integer> totalCounts = new HashMap<>();
    private final List<WorkoutSet> completedSets = new ArrayList<>();
    
    private final Map<String, Integer> currentSetExerciseCounts = new HashMap<>();
    private final List<ExerciseTracker> activeTrackers = new ArrayList<>();
    
    private String activeExerciseCategory = ""; 
    private int currentSetTotalReps = 0;
    private String lastDetectedExercise = "";
    private int totalReps = 0;
    private boolean isResting = false;
    private long resumeTime = 0;
    private static final long RESUME_COOLDOWN_MS = 1000;
    private static final float SMOOTHING_ALPHA = 0.45f;
    private static final long MIN_REP_INTERVAL_MS = 800;
    private static final long FEEDBACK_COOLDOWN_MS = 2000;
    private long lastFeedbackTime = 0;
    
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

    public RepCounter(Context context) {
        loadExercises(context);
        resetSetCounts();
    }

    private void loadExercises(Context context) {
        try {
            InputStream is = context.getAssets().open("exercises.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ExerciseDefinition def = new ExerciseDefinition(obj);
                
                if (def.isBilateral) {
                    String tracker1Label = def.name + " (L)";
                    String tracker2Label = def.name + " (R)";
                    
                    if (def.sideInversion) {
                        if (def.id.equals("bicep_curl")) {
                            tracker1Label = BICEP_CURL_RIGHT; 
                            tracker2Label = BICEP_CURL_LEFT;  
                        } else {
                            tracker1Label = def.name + " (R)";
                            tracker2Label = def.name + " (L)";
                        }
                    }

                    activeTrackers.add(new ExerciseTracker(def, false, tracker1Label));
                    activeTrackers.add(new ExerciseTracker(def, true, tracker2Label));
                    
                    totalCounts.put(tracker1Label, 0);
                    totalCounts.put(tracker2Label, 0);
                    currentSetExerciseCounts.put(tracker1Label, 0);
                    currentSetExerciseCounts.put(tracker2Label, 0);
                } else {
                    String name = def.name;
                    if (def.id.equals("squat")) name = SQUAT;
                    activeTrackers.add(new ExerciseTracker(def, false, name));
                    totalCounts.put(name, 0);
                    currentSetExerciseCounts.put(name, 0);
                }
            }
        } catch (Exception e) {
            Log.e("RepCounter", "Error loading exercises", e);
        }
    }

    private void resetSetCounts() {
        for (String key : totalCounts.keySet()) {
            currentSetExerciseCounts.put(key, 0);
        }
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
        for (ExerciseTracker tracker : activeTrackers) {
            tracker.reset();
        }
        lastDetectedExercise = "";
    }

    public void processLandmarks(List<NormalizedLandmark> landmarks) {
        if (isResting || landmarks.size() < 33) return;

        long now = SystemClock.elapsedRealtime();
        if (now - resumeTime < RESUME_COOLDOWN_MS) {
            return;
        }

        for (ExerciseTracker tracker : activeTrackers) {
            if (activeExerciseCategory.isEmpty() || activeExerciseCategory.equals(tracker.def.name)) {
                tracker.process(landmarks, now);
            }
        }
    }

    private String getCategory(String exercise) {
        if (exercise.contains("Bicep Curl")) return CAT_BICEP_CURL;
        if (exercise.equals(SQUAT)) return CAT_SQUAT;
        for (ExerciseTracker t : activeTrackers) {
            if (t.label.equals(exercise)) return t.def.name;
        }
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
                setCounts.put(BICEP_CURL_LEFT, currentSetExerciseCounts.getOrDefault(BICEP_CURL_LEFT, 0));
                setCounts.put(BICEP_CURL_RIGHT, currentSetExerciseCounts.getOrDefault(BICEP_CURL_RIGHT, 0));
            } else if (activeExerciseCategory.equals(CAT_SQUAT)) {
                setCounts.put(SQUAT, currentSetTotalReps);
            } else {
                for (Map.Entry<String, Integer> entry : currentSetExerciseCounts.entrySet()) {
                    if (getCategory(entry.getKey()).equals(activeExerciseCategory)) {
                        setCounts.put(entry.getKey(), entry.getValue());
                    }
                }
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

    private void incrementCount(String exercise) {
        if (activeExerciseCategory.isEmpty()) {
            startSet(exercise);
        }
        
        totalCounts.put(exercise, totalCounts.getOrDefault(exercise, 0) + 1);
        currentSetExerciseCounts.put(exercise, currentSetExerciseCounts.getOrDefault(exercise, 0) + 1);
        
        currentSetTotalReps++;
        totalReps++;
        lastDetectedExercise = exercise;
        
        if (repListener != null) {
            repListener.onRepCompleted(exercise, currentSetExerciseCounts.get(exercise));
        }
    }

    private static class ExerciseDefinition {
        String id;
        String name;
        boolean isBilateral;
        boolean sideInversion;
        List<Checkpoint> checkpoints = new ArrayList<>();
        List<Constraint> constraints = new ArrayList<>();

        ExerciseDefinition(JSONObject json) throws Exception {
            id = json.getString("id");
            name = json.getString("name");
            isBilateral = json.getBoolean("isBilateral");
            sideInversion = json.optBoolean("sideInversion", false);
            
            JSONArray cpArray = json.getJSONArray("checkpoints");
            for (int i = 0; i < cpArray.length(); i++) {
                checkpoints.add(new Checkpoint(cpArray.getJSONObject(i)));
            }
            
            JSONArray consArray = json.getJSONArray("constraints");
            for (int i = 0; i < consArray.length(); i++) {
                constraints.add(new Constraint(consArray.getJSONObject(i)));
            }
        }
    }

    private static class Checkpoint {
        String type;
        double threshold;
        int[] joints;

        Checkpoint(JSONObject json) throws Exception {
            type = json.getString("type");
            threshold = json.getDouble("threshold");
            JSONArray jArray = json.getJSONArray("joints");
            joints = new int[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) joints[i] = jArray.getInt(i);
        }
    }

    private static class Constraint {
        String type;
        double threshold;
        int[] joints;
        String feedback;

        Constraint(JSONObject json) throws Exception {
            type = json.getString("type");
            threshold = json.getDouble("threshold");
            feedback = json.getString("feedback");
            JSONArray jArray = json.getJSONArray("joints");
            joints = new int[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) joints[i] = jArray.getInt(i);
        }
    }

    private class ExerciseTracker {
        final ExerciseDefinition def;
        final String label;
        int currentCheckpointIndex = 0;
        double smoothedAngle = -1;
        long lastRepTime = 0;
        
        // PRE-MAPPED JOINTS FOR ZERO-ALLOCATION LOOP
        private final List<int[]> mappedCheckpointJoints = new ArrayList<>();
        private final List<int[]> mappedConstraintJoints = new ArrayList<>();

        ExerciseTracker(ExerciseDefinition def, boolean isSwapped, String label) {
            this.def = def;
            this.label = label;
            
            for (Checkpoint cp : def.checkpoints) {
                mappedCheckpointJoints.add(mapJoints(cp.joints, isSwapped));
            }
            for (Constraint c : def.constraints) {
                mappedConstraintJoints.add(mapJoints(c.joints, isSwapped));
            }
        }

        void reset() {
            currentCheckpointIndex = 0;
            smoothedAngle = -1;
        }

        void process(List<NormalizedLandmark> landmarks, long now) {
            // 1. Process Constraints
            for (int i = 0; i < def.constraints.size(); i++) {
                Constraint c = def.constraints.get(i);
                int[] joints = mappedConstraintJoints.get(i);
                
                if (checkVisibility(landmarks, joints)) {
                    double angle = ExerciseUtils.calculateAngle(landmarks.get(joints[0]), landmarks.get(joints[1]), landmarks.get(joints[2]));
                    boolean violated = false;
                    if (c.type.equals("greater_than") && angle > c.threshold) violated = true;
                    else if (c.type.equals("less_than") && angle < c.threshold) violated = true;
                    
                    if (violated) {
                        if (formListener != null && !activeExerciseCategory.isEmpty()) {
                            if (now - lastFeedbackTime > FEEDBACK_COOLDOWN_MS) {
                                formListener.onFormFeedback(c.feedback);
                                lastFeedbackTime = now;
                            }
                        }
                        reset(); 
                        return;
                    }
                }
            }

            // 2. Process Checkpoints
            if (currentCheckpointIndex >= def.checkpoints.size()) currentCheckpointIndex = 0;
            Checkpoint cp = def.checkpoints.get(currentCheckpointIndex);
            int[] joints = mappedCheckpointJoints.get(currentCheckpointIndex);

            if (checkVisibility(landmarks, joints)) {
                double rawAngle = ExerciseUtils.calculateAngle(landmarks.get(joints[0]), landmarks.get(joints[1]), landmarks.get(joints[2]));
                if (smoothedAngle == -1) smoothedAngle = rawAngle;
                smoothedAngle = smoothedAngle + SMOOTHING_ALPHA * (rawAngle - smoothedAngle);

                boolean satisfied = false;
                if (cp.type.equals("greater_than") && smoothedAngle > cp.threshold) satisfied = true;
                else if (cp.type.equals("less_than") && smoothedAngle < cp.threshold) satisfied = true;

                if (satisfied) {
                    currentCheckpointIndex++;
                    if (currentCheckpointIndex >= def.checkpoints.size()) {
                        if (now - lastRepTime > MIN_REP_INTERVAL_MS) {
                            incrementCount(label);
                            lastRepTime = now;
                        }
                        currentCheckpointIndex = 0;
                    }
                }
            }
        }

        private int[] mapJoints(int[] original, boolean isSwapped) {
            if (!isSwapped) return original;
            int[] swapped = new int[original.length];
            for (int i = 0; i < original.length; i++) {
                int j = original[i];
                if (j % 2 != 0) swapped[i] = j + 1; 
                else swapped[i] = j - 1; 
            }
            return swapped;
        }

        private boolean checkVisibility(List<NormalizedLandmark> landmarks, int[] joints) {
            for (int j : joints) {
                if (landmarks.get(j).visibility().orElse(0f) < 0.5f) return false;
            }
            return true;
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
