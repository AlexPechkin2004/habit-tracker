package com.alexpechkin.habittracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

public class HabitNotificationScheduler {
    private static final String TAG = "HabitNotificationScheduler";

    public static void scheduleHabitNotification(Context context, String key, String title, Date triggerTime, boolean isHabit, String message, int reminderInterval) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null for key: " + key);
            return;
        }

        Intent intent = new Intent(context, HabitNotificationReceiver.class);
        intent.putExtra("key", key);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("isHabit", isHabit);
        intent.putExtra("reminderInterval", reminderInterval);

        int requestCode = key.hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(triggerTime);
        long triggerTimeMillis = calendar.getTimeInMillis();

        if (triggerTimeMillis <= System.currentTimeMillis()) {
            if (reminderInterval < 0) {
                calendar.add(Calendar.MINUTE, Math.abs(reminderInterval));
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, reminderInterval > 0 ? reminderInterval : 1);
            }
            triggerTimeMillis = calendar.getTimeInMillis();
        }

        Log.d(TAG, "Scheduling notification for key: " + key + " at " + new Date(triggerTimeMillis) + " with interval: " + reminderInterval);

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
        );

        // Save notification data
        HabitNotificationService.saveNotificationData(context, key, title, new Date(triggerTimeMillis), isHabit, message, reminderInterval);
    }

    public static void cancelScheduledNotification(Context context, String key) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null when cancelling for key: " + key);
            return;
        }

        Intent intent = new Intent(context, HabitNotificationReceiver.class);
        int requestCode = key.hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        Log.d(TAG, "Cancelled notification for key: " + key);

        // Remove notification data
        HabitNotificationService.removeNotificationData(context, key);
    }
}