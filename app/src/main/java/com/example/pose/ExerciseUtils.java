package com.example.pose;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

public class ExerciseUtils {
    /**
     * Calculates the angle between three landmarks.
     * @param first Landmark 1 (e.g., Shoulder)
     * @param mid Landmark 2 (e.g., Elbow)
     * @param last Landmark 3 (e.g., Wrist)
     * @return Angle in degrees
     */
    public static double calculateAngle(NormalizedLandmark first, NormalizedLandmark mid, NormalizedLandmark last) {
        double angle = Math.toDegrees(
                Math.atan2(last.y() - mid.y(), last.x() - mid.x()) -
                Math.atan2(first.y() - mid.y(), first.x() - mid.x())
        );
        angle = Math.abs(angle);
        if (angle > 180) {
            angle = 360 - angle;
        }
        return angle;
    }
}
