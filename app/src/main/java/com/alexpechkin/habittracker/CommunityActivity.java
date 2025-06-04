package com.alexpechkin.habittracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import android.content.Intent;
import android.content.pm.PackageManager;

public class CommunityActivity extends AppCompatActivity {
    private static final String TAG = "CommunityActivity";
    private DatabaseReference database;
    private FirebaseUser currentUser;
    private LinearLayout communityContainer;
    private ScrollView scrollView;
    private String currentChatId;
    private String currentChatName;
    private Handler handler;
    private boolean isAccountability;
    private boolean wasKeyboardShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        database = FirebaseDatabase.getInstance().getReference();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        scrollView = findViewById(R.id.scroll_view);
        communityContainer = findViewById(R.id.community_container);
        handler = new Handler(Looper.getMainLooper());

        currentChatId = getIntent().getStringExtra("CHAT_ID");
        currentChatName = getIntent().getStringExtra("CHAT_NAME");
        isAccountability = getIntent().getBooleanExtra("IS_ACCOUNTABILITY", false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        setupUI();
        loadChatMessages();
        setupKeyboardListener();
        registerFcmToken();
    }

    private void registerFcmToken() {
        if (currentUser == null) return;

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                return;
            }
            String token = task.getResult();
            database.child("users").child(currentUser.getUid()).child("fcmToken").setValue(token)
                    .addOnCompleteListener(task1 -> {
                        if (task1.isSuccessful()) {
                            Log.d(TAG, "FCM token registered successfully");
                        } else {
                            Log.e(TAG, "Failed to register FCM token", task1.getException());
                        }
                    });
        });
    }

    private void setupUI() {
        TextView chatTitle = findViewById(R.id.chat_title);
        chatTitle.setText(currentChatName != null ? currentChatName : getString(R.string.unnamed_chat));

        ImageButton sendMessageButton = findViewById(R.id.send_message_button);
        EditText messageInput = findViewById(R.id.message_input);

        sendMessageButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
                messageInput.setText("");
            } else {
                Toast.makeText(this, R.string.empty_message_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage(String message) {
        if (currentUser == null || currentChatId == null) return;

        String userId = currentUser.getUid();
        DatabaseReference messagesRef = database
                .child(isAccountability ? "accountability_chats" : "chats")
                .child(currentChatId)
                .child("messages")
                .push();

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("userId", userId);
        messageData.put("message", message);
        messageData.put("timestamp", System.currentTimeMillis());

        messagesRef.setValue(messageData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, R.string.message_sent_toast, Toast.LENGTH_SHORT).show();
                scrollToBottom();
                if (isAccountability) {
                    sendNotificationToPartner(message);
                }
            } else {
                Toast.makeText(this, R.string.message_send_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendNotificationToPartner(String message) {
        DatabaseReference chatRef = database
                .child("accountability_chats")
                .child(currentChatId);
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String partnerId = getOtherParticipant(snapshot);
                if (partnerId != null) {
                    database.child("users").child(partnerId).child("fcmToken")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String partnerToken = snapshot.getValue(String.class);
                                    if (partnerToken != null) {
                                        sendFcmNotification(partnerToken, message);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e(TAG, "Error fetching partner FCM token: " + error.getMessage());
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching chat data: " + error.getMessage());
            }
        });
    }

    private String getOtherParticipant(DataSnapshot chatSnapshot) {
        Iterable<DataSnapshot> members = chatSnapshot.child("members").getChildren();
        for (DataSnapshot member : members) {
            String userId = member.getValue(String.class);
            if (userId != null && !userId.equals(currentUser.getUid())) {
                return userId;
            }
        }
        return null;
    }

    private void sendFcmNotification(String partnerToken, String message) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://sendnotification-fkqqwiy3aa-uc.a.run.app"; // Correct endpoint

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("token", partnerToken);
            jsonBody.put("title", currentChatName);
            jsonBody.put("body", message);
            jsonBody.put("chatId", currentChatId);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON: " + e.getMessage());
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                response -> Log.d(TAG, "Notification sent successfully: " + response.toString()),
                error -> Log.e(TAG, "Error sending notification: " + error.getMessage())) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                // Add Firebase ID token for authentication if required
                try {
                    String idToken = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).getResult().getToken();
                    headers.put("Authorization", "Bearer " + idToken);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting ID token", e);
                }
                return headers;
            }
        };

        queue.add(jsonObjectRequest);
    }

    private void loadChatMessages() {
        if (currentChatId == null) return;

        DatabaseReference messagesRef = database
                .child(isAccountability ? "accountability_chats" : "chats")
                .child(currentChatId)
                .child("messages");
        messagesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                communityContainer.removeAllViews();
                for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                    String userId = messageSnapshot.child("userId").getValue(String.class);
                    String message = messageSnapshot.child("message").getValue(String.class);
                    Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);

                    if (userId != null && message != null && timestamp != null) {
                        displayMessage(userId, message, timestamp);
                    }
                }
                handler.postDelayed(CommunityActivity.this::scrollToBottom, 100);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading messages: " + error.getMessage());
                Toast.makeText(CommunityActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayMessage(String userId, String message, long timestamp) {
        CardView card = new CardView(this);
        card.setCardElevation(8);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 8, 16, 8);
        card.setLayoutParams(params);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        card.addView(layout);

        database.child("users").child(userId).child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String username = snapshot.getValue(String.class);
                if (username == null) username = getString(R.string.anonymous_user);

                TextView usernameText = new TextView(CommunityActivity.this);
                usernameText.setText(username);
                usernameText.setTextSize(16);
                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
                usernameText.setTextColor(ContextCompat.getColor(CommunityActivity.this, isDarkTheme ? R.color.teal_200 : R.color.purple_500));
                layout.addView(usernameText);

                TextView messageText = new TextView(CommunityActivity.this);
                messageText.setText(message);
                messageText.setTextSize(14);
                messageText.setPadding(0, 8, 0, 0);
                layout.addView(messageText);

                TextView timestampText = new TextView(CommunityActivity.this);
                timestampText.setText(new SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(timestamp)));
                timestampText.setTextSize(12);
                timestampText.setPadding(0, 8, 0, 0);
                layout.addView(timestampText);

                String finalUsername = username;
                card.setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(CommunityActivity.this);
                    builder.setTitle(R.string.message_options);
                    String[] options = {
                            getString(R.string.copy_message),
                            getString(R.string.report_message)
                    };
                    builder.setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                copyMessage(message);
                                break;
                            case 1:
                                reportMessage(finalUsername, message, timestamp);
                                break;
                        }
                    });
                    builder.setNegativeButton(R.string.cancel_button, null);
                    builder.show();
                });

                scrollToBottom();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching username: " + error.getMessage());
            }
        });

        communityContainer.addView(card);
    }

    private void copyMessage(String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Chat Message", message);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.message_copied, Toast.LENGTH_SHORT).show();
    }

    private void reportMessage(String username, String message, long timestamp) {
        String adminEmail = "email@alexpechkin.com";
        String subject = "Report: Inappropriate Message in Community Chat";
        String body = "Reported by: " + (currentUser != null ? currentUser.getEmail() : "Anonymous") + "\n" +
                "Chat ID: " + currentChatId + "\n" +
                "Chat Name: " + currentChatName + "\n" +
                "Username: " + username + "\n" +
                "Message: " + message + "\n" +
                "Timestamp: " + new SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(timestamp));

        String mailto = "mailto:" + Uri.encode(adminEmail) +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body);
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse(mailto));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        try {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)));
        } catch (Exception e) {
            Log.e(TAG, "Error opening email client: " + e.getMessage());
            Toast.makeText(this, R.string.email_client_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollToBottom() {
        scrollView.post(() -> {
            scrollView.fullScroll(View.FOCUS_DOWN);
            if (communityContainer.getChildCount() > 0) {
                View lastChild = communityContainer.getChildAt(communityContainer.getChildCount() - 1);
                scrollView.scrollTo(0, lastChild.getBottom());
            }
        });
    }

    private void setupKeyboardListener() {
        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private final Rect r = new Rect();

            @Override
            public void onGlobalLayout() {
                // Отримуємо поточні видимі межі rootView
                rootView.getWindowVisibleDisplayFrame(r);

                int screenHeight = rootView.getRootView().getHeight();
                // r.bottom - це нижня координата видимої області.
                // Якщо клавіатура відкрита, r.bottom буде менше, ніж висота всього екрану/кореневого виду.
                int keypadHeight = screenHeight - r.bottom;

                // Вважаємо, що клавіатура показана, якщо вона займає більше 15% висоти екрану.
                // Цей поріг можна налаштувати.
                boolean isKeyboardCurrentlyShown = keypadHeight > screenHeight * 0.15;

                if (isKeyboardCurrentlyShown && !wasKeyboardShown) {
                    // Клавіатура щойно з'явилася
                    scrollToBottom();
                }
                // Немає потреби викликати scrollToBottom при зникненні клавіатури в цьому сценарії,
                // але ви можете додати логіку для isKeyboardCurrentlyShown == false && wasKeyboardShown, якщо потрібно.

                wasKeyboardShown = isKeyboardCurrentlyShown;
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}