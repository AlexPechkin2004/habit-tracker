package com.alexpechkin.habittracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import java.util.Calendar;

public class HabitNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "HabitNotificationReceiver";
    private static final String CHANNEL_ID = "habit_notifications";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Extract intent extras
        String key = intent.getStringExtra("key");
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        boolean isHabit = intent.getBooleanExtra("isHabit", true);
        int reminderInterval = intent.getIntExtra("reminderInterval", 1);

        Log.d(TAG, "Received notification for key: " + key + ", interval: " + reminderInterval);

        // Create Intent for ResultsActivity with tab information
        Intent activityIntent = new Intent(context, ResultsActivity.class);
        activityIntent.putExtra("OPEN_TAB", isHabit ? "habits" : "addictions");
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Create PendingIntent for notification click
        assert key != null;
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                key.hashCode(),
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create and show the notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Habit Notifications", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent); // Attach the PendingIntent

        notificationManager.notify(key.hashCode(), builder.build());
        Log.d(TAG, "Notification sent for key: " + key);

        // Reschedule the next notification for minute-based intervals
        if (isHabit && reminderInterval < 0) {
            Calendar next = Calendar.getInstance();
            next.add(Calendar.MINUTE, Math.abs(reminderInterval));
            Log.d(TAG, "Rescheduling next notification for " + key + " at " + next.getTime());

            HabitNotificationScheduler.scheduleHabitNotification(
                    context, key, title, next.getTime(), true, message, reminderInterval);
        }
    }
}