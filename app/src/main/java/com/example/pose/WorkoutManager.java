package com.example.pose;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class WorkoutManager {
    private static final String FOLDER_NAME = "workouts";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static void saveSession(Context context, ExerciseSession session) {
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                Log.e("WorkoutManager", "Could not create workouts folder");
            }
        }

        String fileName = DATE_FORMAT.format(new Date()) + ".dat";
        File file = new File(folder, fileName);

        List<ExerciseSession> sessions = loadSessionsForDate(context, new Date());
        sessions.add(session);

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(sessions);
        } catch (IOException e) {
            Log.e("WorkoutManager", "Error saving session", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<ExerciseSession> loadSessionsForDate(Context context, Date date) {
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        String fileName = DATE_FORMAT.format(date) + ".dat";
        File file = new File(folder, fileName);

        if (!file.exists()) return new ArrayList<>();

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (List<ExerciseSession>) ois.readObject();
        } catch (Exception e) {
            Log.e("WorkoutManager", "Error loading sessions", e);
            return new ArrayList<>();
        }
    }

    public static Map<String, List<ExerciseSession>> getLast7DaysSessions(Context context) {
        Map<String, List<ExerciseSession>> report = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);

        for (int i = 0; i < 7; i++) {
            Date date = cal.getTime();
            String dateStr = DATE_FORMAT.format(date);
            report.put(dateStr, loadSessionsForDate(context, date));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return report;
    }

    public static int calculateCurrentStreak(Context context) {
        int streak = 0;
        Calendar cal = Calendar.getInstance();
        
        // Check today first
        if (!loadSessionsForDate(context, cal.getTime()).isEmpty()) {
            streak++;
        } else {
            // If no workout today, check if there was one yesterday to continue the streak
            cal.add(Calendar.DAY_OF_YEAR, -1);
            if (loadSessionsForDate(context, cal.getTime()).isEmpty()) {
                return 0; // No workout today or yesterday
            }
            // If there was a workout yesterday, start counting from there
            streak = 0; // We'll increment in the loop
            cal = Calendar.getInstance(); // Reset to today
            cal.add(Calendar.DAY_OF_YEAR, -1); // Start checking from yesterday
        }

        // Check backwards
        while (true) {
            if (streak > 0 && cal.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) {
                // Already checked today
            } else {
               if (loadSessionsForDate(context, cal.getTime()).isEmpty()) break;
               streak++;
            }
            cal.add(Calendar.DAY_OF_YEAR, -1);
            if (streak > 365) break; // Safety break
        }
        
        return streak;
    }
}
