package com.example.pose;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class WorkoutManager {
    private static final String TAG = "WorkoutManager";
    private static final String FOLDER_NAME = "workouts_v2";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static synchronized void saveSession(Context context, ExerciseSession session) {
        // 1. Save Locally
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        if (!folder.exists() && !folder.mkdirs()) {
            Log.e(TAG, "Could not create workouts folder");
        }

        String today = DATE_FORMAT.format(new Date());
        File file = new File(folder, today + ".dat");

        List<ExerciseSession> sessions = loadSessionsForDate(context, new Date());
        sessions.add(session);

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(sessions);
            Log.d(TAG, "Session saved locally for " + today);
        } catch (IOException e) {
            Log.e(TAG, "Error saving session locally", e);
        }

        // 2. Sync to Firebase if logged in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> data = new HashMap<>();
            data.put("sessions", sessions);

            db.collection("users").document(user.getUid())
                    .collection("daily_workouts").document(today)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Synced to Firebase for " + today))
                    .addOnFailureListener(e -> Log.e(TAG, "Firebase sync failed", e));
        }
    }

    @SuppressWarnings("unchecked")
    public static synchronized List<ExerciseSession> loadSessionsForDate(Context context, Date date) {
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        String dateStr = DATE_FORMAT.format(date);
        File file = new File(folder, dateStr + ".dat");

        if (!file.exists()) return new ArrayList<>();

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (List<ExerciseSession>) ois.readObject();
        } catch (Exception e) {
            Log.e(TAG, "Error loading local sessions", e);
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

    public static Map<String, List<ExerciseSession>> getMonthSessions(Context context) {
        Map<String, List<ExerciseSession>> report = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        for (int i = 0; i < daysInMonth; i++) {
            Date date = cal.getTime();
            String dateStr = DATE_FORMAT.format(date);
            List<ExerciseSession> daySessions = loadSessionsForDate(context, date);
            if (!daySessions.isEmpty()) {
                report.put(dateStr, daySessions);
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return report;
    }

    public static int calculateCurrentStreak(Context context) {
        int streak = 0;
        Calendar cal = Calendar.getInstance();
        
        if (!loadSessionsForDate(context, cal.getTime()).isEmpty()) {
            streak++;
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -1);
            if (loadSessionsForDate(context, cal.getTime()).isEmpty()) return 0;
            cal.add(Calendar.DAY_OF_YEAR, 1); 
        }

        cal.add(Calendar.DAY_OF_YEAR, -1);
        while (true) {
            if (loadSessionsForDate(context, cal.getTime()).isEmpty()) break;
            streak++;
            cal.add(Calendar.DAY_OF_YEAR, -1);
            if (streak > 365) break;
        }
        return streak;
    }
}
