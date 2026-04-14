package com.example.pose;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class WorkoutManager {
    private static final String PREF_NAME = "WorkoutPrefs";
    private static final String SESSIONS_KEY = "ExerciseSessions";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static void saveSession(Context context, ExerciseSession session) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<ExerciseSession> sessions = getAllSessions(context);
        sessions.add(session);
        
        String json = new Gson().toJson(sessions);
        prefs.edit().putString(SESSIONS_KEY, json).apply();
    }

    public static List<ExerciseSession> getAllSessions(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(SESSIONS_KEY, null);
        if (json == null) return new ArrayList<>();
        
        Type type = new TypeToken<List<ExerciseSession>>() {}.getType();
        return new Gson().fromJson(json, type);
    }

    public static List<ExerciseSession> loadSessionsForDate(Context context, Date date) {
        String dateKey = dateFormat.format(date);
        List<ExerciseSession> all = getAllSessions(context);
        List<ExerciseSession> filtered = new ArrayList<>();
        for (ExerciseSession s : all) {
            if (dateFormat.format(new Date(s.getTimestamp())).equals(dateKey)) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    public static Map<String, List<ExerciseSession>> getLast7DaysSessions(Context context) {
        List<ExerciseSession> all = getAllSessions(context);
        Map<String, List<ExerciseSession>> result = new LinkedHashMap<>();
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        
        for (int i = 0; i < 7; i++) {
            String dateKey = dateFormat.format(cal.getTime());
            result.put(dateKey, new ArrayList<>());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        for (ExerciseSession s : all) {
            String d = dateFormat.format(new Date(s.getTimestamp()));
            if (result.containsKey(d)) {
                result.get(d).add(s);
            }
        }
        return result;
    }

    public static Map<String, List<ExerciseSession>> getMonthSessions(Context context) {
        List<ExerciseSession> all = getAllSessions(context);
        Map<String, List<ExerciseSession>> result = new HashMap<>();
        
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);

        for (ExerciseSession s : all) {
            cal.setTimeInMillis(s.getTimestamp());
            if (cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year) {
                String d = dateFormat.format(new Date(s.getTimestamp()));
                result.computeIfAbsent(d, k -> new ArrayList<>()).add(s);
            }
        }
        return result;
    }

    public static int calculateCurrentStreak(Context context) {
        List<ExerciseSession> all = getAllSessions(context);
        if (all.isEmpty()) return 0;

        Set<String> dates = new HashSet<>();
        for (ExerciseSession s : all) {
            dates.add(dateFormat.format(new Date(s.getTimestamp())));
        }

        Calendar cal = Calendar.getInstance();
        int streak = 0;
        while (true) {
            String d = dateFormat.format(cal.getTime());
            if (dates.contains(d)) {
                streak++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    public static List<ExerciseMuscleInfo> getExerciseMuscleInfo(Context context) {
        List<ExerciseMuscleInfo> list = new ArrayList<>();
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
                ExerciseMuscleInfo info = new ExerciseMuscleInfo();
                info.id = obj.getString("id");
                info.name = obj.getString("name");
                info.muscles = new HashMap<>();
                if (obj.has("muscles")) {
                    JSONArray mArray = obj.getJSONArray("muscles");
                    for (int j = 0; j < mArray.length(); j++) {
                        JSONObject mObj = mArray.getJSONObject(j);
                        info.muscles.put(
                            MuscleHeatmapView.MuscleGroup.valueOf(mObj.getString("group")),
                            (float) mObj.getDouble("factor")
                        );
                    }
                }
                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static class ExerciseMuscleInfo {
        public String id;
        public String name;
        public Map<MuscleHeatmapView.MuscleGroup, Float> muscles;
    }
}
