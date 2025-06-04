package com.alexpechkin.habittracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "habit_tracker_notifications";
    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        if (!data.isEmpty()) {
            String title = data.get("title");
            String body = data.get("body");
            String chatId = data.get("chatId");
            Log.d(TAG, "Notification received: Title=" + title + ", Body=" + body + ", ChatId=" + chatId);

            sendNotification(title, body, chatId);
        }
    }



    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token: " + token);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseDatabase.getInstance().getReference()
                    .child("users").child(userId).child("fcmToken").setValue(token)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "FCM token saved for user: " + userId);
                        } else {
                            Log.e(TAG, "Failed to save FCM token", task.getException());
                        }
                    });
        } else {
            Log.w(TAG, "No authenticated user, skipping FCM token save");
            // Optionally store token locally and save later when user logs in
        }
    }

    private void sendNotification(String title, String messageBody, String chatId) {
        String safeTitle = title != null ? title : "Чат";
        String safeMessage = messageBody != null ? messageBody : "";
        String safeChatId = chatId != null ? chatId : "default_chat";

        String PREFS_NAME = "chat_notifications";
        String key = "chat_" + safeChatId;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String oldMessages = prefs.getString(key, "");
        long now = System.currentTimeMillis();
        String sender = safeTitle.replace("New Message in ", "");

        String newEntry = now + "|" + sender + "|" + safeMessage.replace("\n", " ") + "\n";
        String allMessages = oldMessages + newEntry;

        String[] lines = allMessages.split("\n");
        int maxMessages = 10;
        if (lines.length > maxMessages) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - maxMessages; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            allMessages = sb.toString();
        }
        prefs.edit().putString(key, allMessages).apply();

        NotificationCompat.MessagingStyle messagingStyle =
                new NotificationCompat.MessagingStyle("Me")
                        .setConversationTitle(sender); // або null для 1-на-1

        for (String line : allMessages.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) continue;
            long ts;
            try {
                ts = Long.parseLong(parts[0]);
            } catch (Exception e) {
                ts = System.currentTimeMillis();
            }
            String msgSender = parts[1];
            String msgText = parts[2];
            messagingStyle.addMessage(msgText, ts, msgSender);
        }

        Intent intent = new Intent(this, CommunityActivity.class);
        intent.putExtra("CHAT_ID", safeChatId);
        intent.putExtra("CHAT_NAME", sender);
        intent.putExtra("IS_ACCOUNTABILITY", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, safeChatId.hashCode(), intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setStyle(messagingStyle)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVibrate(new long[]{0, 250, 250, 250})
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Habit Tracker Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            channel.setDescription("Chat and habit notifications");
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(safeChatId.hashCode(), notificationBuilder.build());
    }
}