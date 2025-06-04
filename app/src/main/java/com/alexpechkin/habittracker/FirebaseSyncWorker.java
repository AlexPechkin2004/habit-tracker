package com.alexpechkin.habittracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FirebaseSyncWorker extends Worker {
    private static final String TAG = "FirebaseSyncWorker";
    private static final String PENDING_CHANGES_PREFS = "PendingChangesPrefs";
    private static final String PENDING_CHANGES_KEY = "pending_changes";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public FirebaseSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting Firebase sync in background");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, skipping sync");
            return Result.success();
        }

        if (!HabitUtils.isNetworkAvailable(getApplicationContext())) {
            Log.w(TAG, "No network available, retrying later");
            return Result.retry();
        }

        SharedPreferences pendingChangesPrefs = getApplicationContext().getSharedPreferences(PENDING_CHANGES_PREFS, Context.MODE_PRIVATE);
        Set<String> pendingChanges = new HashSet<>(pendingChangesPrefs.getStringSet(PENDING_CHANGES_KEY, new HashSet<>()));

        if (pendingChanges.isEmpty()) {
            Log.d(TAG, "No pending changes to sync");
            return Result.success();
        }

        SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences("HabitsPrefs", Context.MODE_PRIVATE);
        SharedPreferences statsPrefs = getApplicationContext().getSharedPreferences("StatsPrefs", Context.MODE_PRIVATE);
        SharedPreferences goalsPrefs = getApplicationContext().getSharedPreferences("GoalsPrefs", Context.MODE_PRIVATE);
        DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        String userId = currentUser.getUid();

        for (String change : pendingChanges) {
            String[] parts = change.split(":");
            String changeType = parts[0];
            String key = parts.length > 1 ? parts[1] : null;

            try {
                if ("keys_and_labels".equals(changeType)) {
                    // Sync habits, addictions, labels, and goals
                    Set<String> habitKeys = sharedPrefs.getStringSet("selectedHabits", new HashSet<>());
                    Set<String> addictionKeys = sharedPrefs.getStringSet("selectedAddictions", new HashSet<>());
                    Map<String, String> habitLabels = new HashMap<>();
                    Map<String, String> addictionLabels = new HashMap<>();
                    Map<String, ResultsActivity.Goal> goals = new HashMap<>();

                    // Load labels from SharedPreferences
                    Map<String, ?> allPrefs = sharedPrefs.getAll();
                    for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                        String prefKey = entry.getKey();
                        if (prefKey.startsWith("custom_habit_") && entry.getValue() instanceof String) {
                            habitLabels.put(prefKey, (String) entry.getValue());
                        } else if (prefKey.startsWith("custom_addiction_") && entry.getValue() instanceof String) {
                            addictionLabels.put(prefKey, (String) entry.getValue());
                        }
                    }

                    // Load goals
                    Map<String, ?> allGoalPrefs = goalsPrefs.getAll();
                    Set<String> goalIds = new HashSet<>();
                    for (String prefKey : allGoalPrefs.keySet()) {
                        if (prefKey.endsWith("_habitKey")) {
                            goalIds.add(prefKey.replace("_habitKey", ""));
                        }
                    }
                    for (String goalId : goalIds) {
                        ResultsActivity.Goal goal = new ResultsActivity.Goal();
                        goal.habitKey = goalsPrefs.getString(goalId + "_habitKey", "");
                        goal.type = goalsPrefs.getString(goalId + "_type", "");
                        goal.target = goalsPrefs.getInt(goalId + "_target", 0);
                        goal.achieved = goalsPrefs.getBoolean(goalId + "_achieved", false);
                        try {
                            String setDateStr = goalsPrefs.getString(goalId + "_setDate", null);
                            goal.setDate = setDateStr != null ? dateFormat.parse(setDateStr) : new Date();
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing goal set date: " + e.getMessage());
                            goal.setDate = new Date();
                        }
                        goals.put(goalId, goal);
                    }

                    // Save to Firebase
                    DatabaseReference userRef = database.child("users").child(userId);
                    userRef.child("habits").setValue(new ArrayList<>(habitKeys));
                    userRef.child("addictions").setValue(new ArrayList<>(addictionKeys));
                    if (!habitLabels.isEmpty()) {
                        userRef.child("habit_labels").setValue(habitLabels);
                    }
                    if (!addictionLabels.isEmpty()) {
                        userRef.child("addiction_labels").setValue(addictionLabels);
                    }
                    for (Map.Entry<String, ResultsActivity.Goal> entry : goals.entrySet()) {
                        String goalId = entry.getKey();
                        ResultsActivity.Goal goal = entry.getValue();
                        DatabaseReference goalRef = database.child("users").child(userId).child("goals").child(goalId);
                        Map<String, Object> goalMap = new HashMap<>();
                        goalMap.put("habitKey", goal.habitKey);
                        goalMap.put("type", goal.type);
                        goalMap.put("target", goal.target);
                        goalMap.put("achieved", goal.achieved);
                        goalMap.put("setDate", dateFormat.format(goal.setDate));
                        goalRef.setValue(goalMap);
                    }

                    Log.d(TAG, "Synced keys_and_labels for user: " + userId);
                } else if (changeType.startsWith("stats_")) {
                    // Sync stats for specific habit or addiction
                    boolean isHabit = sharedPrefs.getStringSet("selectedHabits", new HashSet<>()).contains(key);
                    ResultsActivity.HabitStats stats = new ResultsActivity.HabitStats();
                    try {
                        String startDateStr = statsPrefs.getString(key + "_start_date", null);
                        stats.startDate = startDateStr != null ? dateFormat.parse(startDateStr) : new Date();
                        stats.progress = statsPrefs.getInt(key + "_progress", 0);
                        String nextReminderStr = statsPrefs.getString(key + "_next_reminder", null);
                        stats.nextReminder = nextReminderStr != null ? dateFormat.parse(nextReminderStr) : new Date();
                        String lastCheckStr = statsPrefs.getString(key + "_last_check_date", null);
                        stats.lastCheckDate = lastCheckStr != null ? dateFormat.parse(lastCheckStr) : stats.startDate;
                        stats.reminderInterval = statsPrefs.getInt(key + "_reminder_interval", 1);
                        stats.reminderHour = statsPrefs.getInt(key + "_reminder_hour", 18);
                        stats.reminderMinute = statsPrefs.getInt(key + "_reminder_minute", 0);
                        stats.markedDays = new ArrayList<>();
                        Set<String> markedDaysSet = statsPrefs.getStringSet(key + "_marked_days", new HashSet<>());
                        for (String dateStr : markedDaysSet) {
                            Date date = dateOnlyFormat.parse(dateStr);
                            if (date != null) stats.markedDays.add(date);
                        }
                        stats.relapseDates = new ArrayList<>();
                        Set<String> relapseDatesSet = statsPrefs.getStringSet(key + "_relapse_dates", new HashSet<>());
                        for (String dateStr : relapseDatesSet) {
                            Date date = dateFormat.parse(dateStr);
                            if (date != null) stats.relapseDates.add(date);
                        }
                        stats.completionTimestamps = new ArrayList<>();
                        Set<String> completionTimestampsSet = statsPrefs.getStringSet(key + "_completion_timestamps", new HashSet<>());
                        for (String timestampStr : completionTimestampsSet) {
                            Date timestamp = dateFormat.parse(timestampStr);
                            if (timestamp != null) stats.completionTimestamps.add(timestamp);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing stats for key: " + key + ", " + e.getMessage());
                    }

                    assert key != null;
                    DatabaseReference statsRef = database.child("users").child(userId)
                            .child(isHabit ? "habit_stats" : "addiction_stats").child(key);
                    Map<String, Object> statsMap = new HashMap<>();
                    statsMap.put("start_date", dateFormat.format(stats.startDate));
                    statsMap.put("progress", stats.progress);
                    statsMap.put("reminder_hour", stats.reminderHour);
                    statsMap.put("reminder_minute", stats.reminderMinute);
                    if (isHabit) {
                        statsMap.put("next_reminder", dateFormat.format(stats.nextReminder));
                        statsMap.put("reminder_interval", stats.reminderInterval);
                    }
                    if (stats.lastCheckDate != null) {
                        statsMap.put("last_check_date", dateFormat.format(stats.lastCheckDate));
                    }
                    List<String> markedDaysStrings = new ArrayList<>();
                    for (Date date : stats.markedDays) {
                        markedDaysStrings.add(dateOnlyFormat.format(date));
                    }
                    statsMap.put("marked_days", markedDaysStrings);
                    List<String> relapseDatesStrings = new ArrayList<>();
                    for (Date date : stats.relapseDates) {
                        relapseDatesStrings.add(dateFormat.format(date));
                    }
                    statsMap.put("relapse_dates", relapseDatesStrings);
                    List<String> completionTimestampsStrings = new ArrayList<>();
                    for (Date timestamp : stats.completionTimestamps) {
                        completionTimestampsStrings.add(dateFormat.format(timestamp));
                    }
                    statsMap.put("completion_timestamps", completionTimestampsStrings);
                    statsRef.setValue(statsMap);

                    Log.d(TAG, "Synced stats for key: " + key);
                } else if ("settings".equals(changeType)) {
                    // Sync settings
                    SharedPreferences notifPrefs = getApplicationContext().getSharedPreferences("NotificationSettings", Context.MODE_PRIVATE);
                    SharedPreferences addictionPrefs = getApplicationContext().getSharedPreferences("AddictionReminderSettings", Context.MODE_PRIVATE);
                    DatabaseReference settingsRef = database.child("users").child(userId).child("settings");
                    Map<String, Object> settingsMap = new HashMap<>();
                    settingsMap.put("sleep_mode_enabled", notifPrefs.getBoolean("sleep_mode_enabled", false));
                    settingsMap.put("sleep_start_hour", notifPrefs.getInt("sleep_start_hour", 22));
                    settingsMap.put("sleep_start_minute", notifPrefs.getInt("sleep_start_minute", 0));
                    settingsMap.put("sleep_end_hour", notifPrefs.getInt("sleep_end_hour", 7));
                    settingsMap.put("sleep_end_minute", notifPrefs.getInt("sleep_end_minute", 0));
                    settingsMap.put("addiction_reminders_enabled", addictionPrefs.getBoolean("addiction_reminders_enabled", false));
                    settingsMap.put("addiction_reminder_hour", addictionPrefs.getInt("addiction_reminder_hour", 18));
                    settingsMap.put("addiction_reminder_minute", addictionPrefs.getInt("addiction_reminder_minute", 0));
                    settingsRef.setValue(settingsMap);

                    Log.d(TAG, "Synced settings for user: " + userId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing change: " + change + ", " + e.getMessage());
                return Result.retry();
            }
        }

        // Clear pending changes after successful sync
        pendingChangesPrefs.edit().remove(PENDING_CHANGES_KEY).apply();
        Log.d(TAG, "Firebase sync completed successfully");
        return Result.success();
    }
}