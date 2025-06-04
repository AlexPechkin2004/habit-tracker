package com.alexpechkin.habittracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HabitNotificationService extends Service {
    private static final String CHANNEL_ID = "habit_notifications_channel";
    private static final String PREFS_NAME = "HabitPrefs";
    private static final String NOTIFICATIONS_KEY = "scheduled_notifications";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundNotification();
        rescheduleAllNotifications();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Habit Tracker")
                .setContentText(getString(R.string.notification_content))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSound(null)
                .setVibrate(null)
                .setShowWhen(false)
                .build();
        startForeground(1, notification);
    }

    private void rescheduleAllNotifications() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String notificationsJson = prefs.getString(NOTIFICATIONS_KEY, null);
        if (notificationsJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<NotificationData>>(){}.getType();
            List<NotificationData> notifications = gson.fromJson(notificationsJson, type);

            for (NotificationData data : notifications) {
                HabitNotificationScheduler.scheduleHabitNotification(
                        this,
                        data.key,
                        data.title,
                        new Date(data.triggerTime),
                        data.isHabit,
                        data.message,
                        data.reminderInterval
                );
            }
        }
    }

    // Helper class to store notification data
    private static class NotificationData {
        String key;
        String title;
        long triggerTime;
        boolean isHabit;
        String message;
        int reminderInterval;

        NotificationData(String key, String title, long triggerTime, boolean isHabit, String message, int reminderInterval) {
            this.key = key;
            this.title = title;
            this.triggerTime = triggerTime;
            this.isHabit = isHabit;
            this.message = message;
            this.reminderInterval = reminderInterval;
        }
    }

    public static void saveNotificationData(Context context, String key, String title, Date triggerTime, boolean isHabit, String message, int reminderInterval) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Gson gson = new Gson();
        String existingJson = prefs.getString(NOTIFICATIONS_KEY, null);
        List<NotificationData> notifications = new ArrayList<>();

        if (existingJson != null) {
            Type type = new TypeToken<List<NotificationData>>(){}.getType();
            notifications = gson.fromJson(existingJson, type);
        }

        notifications.removeIf(data -> data.key.equals(key));
        notifications.add(new NotificationData(key, title, triggerTime.getTime(), isHabit, message, reminderInterval));

        editor.putString(NOTIFICATIONS_KEY, gson.toJson(notifications));
        editor.apply();
    }

    public static void removeNotificationData(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Gson gson = new Gson();
        String existingJson = prefs.getString(NOTIFICATIONS_KEY, null);
        if (existingJson != null) {
            Type type = new TypeToken<List<NotificationData>>(){}.getType();
            List<NotificationData> notifications = gson.fromJson(existingJson, type);

            notifications.removeIf(data -> data.key.equals(key));

            editor.putString(NOTIFICATIONS_KEY, gson.toJson(notifications));
            editor.apply();
        }
    }
}