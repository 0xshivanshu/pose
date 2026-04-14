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

    private String activeExerciseId = "";
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

    private final List<ExerciseTracker> activeTrackers = new ArrayList<>();

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

    public void setRepListener(RepListener listener) { this.repListener = listener; }
    public void setFormListener(FormListener listener) { this.formListener = listener; }
    public void setSetListener(SetListener listener) { this.setListener = listener; }

    public RepCounter(Context context) {
        loadExercises(context);
    }

    private void loadExercises(Context context) {
        try {
            InputStream is = context.getAssets().open("exercises.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            int read = is.read(buffer);
            is.close();
            if (read > 0) {
                String json = new String(buffer, 0, read, StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(json);

                for (int i = 0; i < array.length(); i++) {
                    ExerciseDefinition def = new ExerciseDefinition(array.getJSONObject(i));

                    if (def.isBilateral) {
                        String labelL = def.name + " (L)";
                        String labelR = def.name + " (R)";

                        String tracker1Label = def.sideInversion ? labelR : labelL;
                        String tracker2Label = def.sideInversion ? labelL : labelR;

                        activeTrackers.add(new ExerciseTracker(def, false, tracker1Label)); // Phys Left
                        activeTrackers.add(new ExerciseTracker(def, true, tracker2Label));  // Phys Right
                        totalCounts.put(tracker1Label, 0);
                        totalCounts.put(tracker2Label, 0);
                    } else {
                        activeTrackers.add(new ExerciseTracker(def, false, def.name));
                        totalCounts.put(def.name, 0);
                    }
                }
            }
            resetSetCounts();
        } catch (Exception e) {
            Log.e("RepCounter", "Error loading exercises", e);
        }
    }

    private void resetSetCounts() {
        currentSetExerciseCounts.clear();
        for (String key : totalCounts.keySet()) {
            currentSetExerciseCounts.put(key, 0);
        }
        currentSetTotalReps = 0;
    }

    public boolean isResting() { return isResting; }

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
        if (SystemClock.elapsedRealtime() - resumeTime < RESUME_COOLDOWN_MS) return;

        for (ExerciseTracker tracker : activeTrackers) {
            if (activeExerciseCategory.isEmpty() || activeExerciseCategory.equals(tracker.definition.name)) {
                // Pass current rep count for this specific tracker to handle "First Rep" rules
                Integer count = currentSetExerciseCounts.get(tracker.label);
                tracker.process(landmarks, count == null ? 0 : count);
            }
        }
    }

    private void startSet(String exerciseId, String category) {
        activeExerciseId = exerciseId;
        activeExerciseCategory = category;
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
            for (Map.Entry<String, Integer> entry : currentSetExerciseCounts.entrySet()) {
                Integer val = entry.getValue();
                if (val != null && val > 0) {
                    setCounts.put(entry.getKey(), val);
                }
            }

            WorkoutSet set = new WorkoutSet(activeExerciseId, activeExerciseCategory, setCounts, duration);
            completedSets.add(set);
            if (setListener != null) {
                setListener.onSetFinished(set);
            }
        }
        activeExerciseId = "";
        activeExerciseCategory = "";
        resetSetCounts();
        setStartTime = 0;
    }

    private void incrementCount(String label, String exerciseId, String category) {
        if (activeExerciseCategory.isEmpty()) {
            startSet(exerciseId, category);
        }

        Integer total = totalCounts.get(label);
        totalCounts.put(label, (total == null ? 0 : total) + 1);

        Integer current = currentSetExerciseCounts.get(label);
        int newVal = (current == null ? 0 : current) + 1;
        currentSetExerciseCounts.put(label, newVal);

        currentSetTotalReps++;
        totalReps++;
        lastDetectedExercise = label;

        if (repListener != null) {
            repListener.onRepCompleted(label, newVal);
        }
    }

    public String getCurrentSetDisplayString() {
        if (activeExerciseCategory.isEmpty()) return "0";
        
        StringBuilder sb = new StringBuilder();
        boolean hasMultiple = false;
        int countWithReps = 0;

        for (Map.Entry<String, Integer> entry : currentSetExerciseCounts.entrySet()) {
            String label = entry.getKey();
            if (label.startsWith(activeExerciseCategory)) {
                countWithReps++;
                if (label.contains("(") && label.contains(")")) {
                    hasMultiple = true;
                    String part = label.substring(label.indexOf("(") + 1, label.indexOf(")"));
                    sb.append(part).append(": ").append(entry.getValue()).append("  ");
                }
            }
        }

        if (hasMultiple) {
            return sb.toString().trim();
        } else {
            return String.valueOf(currentSetTotalReps);
        }
    }

    private static class ExerciseDefinition {
        String id, name;
        boolean isBilateral, sideInversion;
        List<Checkpoint> checkpoints = new ArrayList<>();
        List<Constraint> constraints = new ArrayList<>();

        ExerciseDefinition(JSONObject json) throws Exception {
            id = json.getString("id");
            name = json.getString("name");
            isBilateral = json.getBoolean("isBilateral");
            sideInversion = json.getBoolean("sideInversion");

            JSONArray cpArray = json.getJSONArray("checkpoints");
            for (int i = 0; i < cpArray.length(); i++) {
                checkpoints.add(new Checkpoint(cpArray.getJSONObject(i)));
            }

            if (json.has("constraints")) {
                JSONArray cArray = json.getJSONArray("constraints");
                for (int i = 0; i < cArray.length(); i++) {
                    constraints.add(new Constraint(cArray.getJSONObject(i)));
                }
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
        String type, feedback;
        double threshold;
        int[] joints;

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
        final ExerciseDefinition definition;
        final boolean isRightSide;
        final String label;
        int currentCheckpointIndex = 0;
        long lastRepTime = 0;
        double smoothedAngle = -1;
        private static final float SMOOTHING_ALPHA = 0.45f;
        private static final long MIN_REP_INTERVAL_MS = 800;

        ExerciseTracker(ExerciseDefinition def, boolean isRightSide, String label) {
            this.definition = def;
            this.isRightSide = isRightSide;
            this.label = label;
        }

        void reset() {
            currentCheckpointIndex = 0;
            smoothedAngle = -1;
        }

        void process(List<NormalizedLandmark> landmarks, int currentCount) {
            if (currentCheckpointIndex >= definition.checkpoints.size()) return;
            Checkpoint cp = definition.checkpoints.get(currentCheckpointIndex);
            int[] mappedJoints = mapJoints(cp.joints);
            if (!checkVisibility(landmarks, mappedJoints)) return;

            double rawVal = calculateValue(landmarks, mappedJoints);
            if (smoothedAngle < 0) smoothedAngle = rawVal;
            smoothedAngle = smoothedAngle + SMOOTHING_ALPHA * (rawVal - smoothedAngle);

            // Rule 1: Zero form feedback during the first rep of a set (currentCount == 0).
            // Coaching and correcting (resets) only start on the second rep (currentCount >= 1).
            boolean coachingStarted = currentCount >= 1;

            // Rule 2: Avoid nagging when not actively moving.
            // We only coach if the user has moved significantly (25 degrees) away from the start gate.
            boolean isMovingThroughRep = false;
            if (currentCheckpointIndex > 0) {
                double startThreshold = definition.checkpoints.get(0).threshold;
                if (Math.abs(smoothedAngle - startThreshold) >= 25) {
                    isMovingThroughRep = true;
                }
            }

            // Only provide form feedback and enforce correcting resets if coaching has started.
            if (coachingStarted && isMovingThroughRep) {
                for (Constraint c : definition.constraints) {
                    int[] constraintMapped = mapJoints(c.joints);
                    if (!checkVisibility(landmarks, constraintMapped)) continue;
                    double val = calculateValue(landmarks, constraintMapped);
                    if (isThresholdMet(val, c.threshold, c.type)) {
                        if (formListener != null) {
                            formListener.onFormFeedback(c.feedback);
                        }
                        // CORRECTING: Stop the current rep and force a reset if form is bad.
                        reset();
                        return;
                    }
                }
            }

            // Check if we hit the current checkpoint
            if (isThresholdMet(smoothedAngle, cp.threshold, cp.type)) {
                currentCheckpointIndex++;
                if (currentCheckpointIndex >= definition.checkpoints.size()) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastRepTime > MIN_REP_INTERVAL_MS) {
                        incrementCount(label, definition.id, definition.name);
                        lastRepTime = now;
                    }
                    currentCheckpointIndex = 0;
                }
            }
        }

        private int[] mapJoints(int[] joints) {
            if (!isRightSide) return joints;
            int[] mapped = new int[joints.length];
            for (int i = 0; i < joints.length; i++) {
                int j = joints[i];
                if (j >= 11 && j <= 32) {
                    mapped[i] = (j % 2 == 0) ? j - 1 : j + 1;
                } else {
                    mapped[i] = j;
                }
            }
            return mapped;
        }

        private boolean checkVisibility(List<NormalizedLandmark> landmarks, int[] joints) {
            for (int j : joints) {
                if (j < 0 || j >= landmarks.size()) return false;
                NormalizedLandmark landmark = landmarks.get(j);
                if (landmark.visibility().orElse(0f) < 0.5f) return false;
            }
            return true;
        }

        private double calculateValue(List<NormalizedLandmark> landmarks, int[] joints) {
            if (joints.length == 3) {
                return ExerciseUtils.calculateAngle(landmarks.get(joints[0]), landmarks.get(joints[1]), landmarks.get(joints[2]));
            }
            return 0;
        }

        private boolean isThresholdMet(double val, double threshold, String type) {
            if ("greater_than".equals(type)) return val > threshold;
            if ("less_than".equals(type)) return val < threshold;
            return false;
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
