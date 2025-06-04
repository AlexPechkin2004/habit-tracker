package com.alexpechkin.habittracker;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

import android.widget.ArrayAdapter;

import android.widget.Spinner;

import android.content.res.Configuration;

import java.util.Arrays;

public class ResultsActivity extends AppCompatActivity {
    private static final String TAG = "ResultsActivity";
    @SuppressLint("InlinedApi")
    private static final String NOTIFICATION_PERMISSION = android.Manifest.permission.POST_NOTIFICATIONS;

    // Firebase and SharedPreferences
    private DatabaseReference database;
    private FirebaseUser currentUser;
    private SharedPreferences sharedPrefs;
    private SharedPreferences statsPrefs;
    private SharedPreferences notifPrefs;
    private SharedPreferences addictionPrefs;

    // UI Containers
    private LinearLayout homeContainer;
    private ScrollView habitsContainer;
    private ScrollView addictionsContainer;
    private LinearLayout messagesContainer;
    private LinearLayout settingsContainer;
    private LinearLayout habitsListContainer;
    private LinearLayout addictionsListContainer;

    // Data
    private Set<String> habitKeys = new HashSet<>();
    private Set<String> addictionKeys = new HashSet<>();
    private final Map<String, HabitStats> habitStats = new HashMap<>();
    private final Map<String, HabitStats> addictionStats = new HashMap<>();
    private boolean isDataLoaded = false;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // Sleep Mode
    private boolean sleepModeEnabled;
    private int sleepStartHour = 22;
    private int sleepStartMinute = 0;
    private int sleepEndHour = 7;
    private int sleepEndMinute = 0;

    // Addiction Reminder
    private boolean addictionRemindersEnabled;
    private int addictionReminderHour = 18;
    private int addictionReminderMinute = 0;

    private final Handler streakUpdateHandler = new Handler();
    private final Map<String, Runnable> streakUpdateRunnables = new HashMap<>();

    // Firebase and SharedPreferences
    private SharedPreferences goalsPrefs;

    private final Handler dailyCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable dailyCheckRunnable;

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                showHomeScreen();
                return true;
            } else if (itemId == R.id.navigation_habits) {
                showHabitsScreen();
                return true;
            } else if (itemId == R.id.navigation_addictions) {
                showAddictionsScreen();
                return true;
            } else if (itemId == R.id.navigation_messages) {
                showMessagesScreen();
                return true;
            } else if (itemId == R.id.navigation_settings) {
                showSettingsScreen();
                return true;
            }
            return false;
        });
    }

    private void checkPermissions() {
        ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        showNotificationPermissionExplanation();
                    }
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, NOTIFICATION_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(NOTIFICATION_PERMISSION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            }
        }
    }

    private void startNotificationService() {
        Intent serviceIntent = new Intent(this, HabitNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void clearLocalData() {
        sharedPrefs.edit().clear().apply();
        statsPrefs.edit().clear().apply();
        habitKeys.clear();
        addictionKeys.clear();
        habitStats.clear();
        addictionStats.clear();
    }

    private void loadStatsFromFirebase(DataSnapshot snapshot) {
        habitStats.clear();
        addictionStats.clear();
        if (snapshot.child("habit_stats").exists()) {
            for (DataSnapshot statSnapshot : snapshot.child("habit_stats").getChildren()) {
                String key = statSnapshot.getKey();
                if (key != null) habitStats.put(key, parseStatsFromFirebase(statSnapshot));
            }
        }
        if (snapshot.child("addiction_stats").exists()) {
            for (DataSnapshot statSnapshot : snapshot.child("addiction_stats").getChildren()) {
                String key = statSnapshot.getKey();
                if (key != null) addictionStats.put(key, parseStatsFromFirebase(statSnapshot));
            }
        }
        saveAllStatsToPreferences();
    }

    private void loadStatsFromPreferences() {
        habitStats.clear();
        addictionStats.clear();
        for (String key : habitKeys) {
            habitStats.put(key, getStatsFromPreferences(key));
        }
        for (String key : addictionKeys) {
            addictionStats.put(key, getStatsFromPreferences(key));
        }
    }

    private void saveAllStatsToPreferences() {
        SharedPreferences.Editor editor = statsPrefs.edit();
        editor.clear();
        for (Map.Entry<String, HabitStats> entry : habitStats.entrySet()) {
            saveStatsToPreferences(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, HabitStats> entry : addictionStats.entrySet()) {
            saveStatsToPreferences(entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    private void displayData() {
        createHabitCards(habitKeys, habitStats, habitsListContainer, true);
        createHabitCards(addictionKeys, addictionStats, addictionsListContainer, false);
        createHomeScreen();
    }

    private CardView createStatsCard(String progressText, String bestText) {
        CardView card = new CardView(this);
        card.setCardElevation(8);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(32, 8, 32, 16);
        card.setLayoutParams(params);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        card.addView(layout);

        TextView progress = new TextView(this);
        progress.setText(progressText);
        layout.addView(progress);

        TextView best = new TextView(this);
        best.setText(bestText);
        best.setPadding(0, 8, 0, 0);
        layout.addView(best);

        return card;
    }

    private int getAverageProgress(Map<String, HabitStats> statsMap) {
        if (statsMap.isEmpty()) return 0;
        int total = 0;
        for (HabitStats stats : statsMap.values()) {
            total += stats.progress;
        }
        return total / statsMap.size();
    }

    private String getBestHabit(Set<String> keys, Map<String, HabitStats> statsMap) {
        String bestKey = "";
        int bestProgress = -1;
        for (String key : keys) {
            HabitStats stats = statsMap.get(key);
            if (stats != null && stats.progress > bestProgress) {
                bestProgress = stats.progress;
                bestKey = key;
            }
        }
        return bestKey;
    }

    private void showPanicAnimation() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_panic_animation);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.8);
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.4);
            window.setAttributes(params);
        }

        TextView messageText = dialog.findViewById(R.id.panic_message_text);
        messageText.setTextSize(24);
        messageText.setGravity(Gravity.CENTER);
        messageText.setTextColor(Color.WHITE);

        Button closeButton = dialog.findViewById(R.id.close_panic_button);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        closeButton.setBackgroundColor(ContextCompat.getColor(closeButton.getContext(), R.color.purple_500));

        String[] messages = {
                getString(R.string.panic_message_breathe_in),
                getString(R.string.panic_message_breathe_out),
                getString(R.string.panic_message_remember_why),
                getString(R.string.panic_message_you_are_strong),
                getString(R.string.panic_message_one_step)
        };

        final int[] currentMessageIndex = {0};
        final long FADE_DURATION = 1000; // 2 seconds per message

        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] messageRunnable = new Runnable[1];
        messageRunnable[0] = () -> {
            messageText.setText(messages[currentMessageIndex[0]]);
            messageText.setAlpha(0f);
            messageText.animate()
                    .alpha(1f)
                    .setDuration(FADE_DURATION)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            messageText.animate()
                                    .alpha(0f)
                                    .setDuration(FADE_DURATION)
                                    .setStartDelay(FADE_DURATION)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            currentMessageIndex[0]++;
                                            if (currentMessageIndex[0] >= messages.length) {
                                                currentMessageIndex[0] = 0; // Loop back to the first message
                                            }
                                            handler.post(messageRunnable[0]); // Continue the cycle
                                        }
                                    });
                        }
                    }).start();
        };

        handler.post(messageRunnable[0]);
        dialog.show();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void showReminderDialog(String key, HabitStats stats) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.reminder_dialog_title));

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reminder_settings, null);
        RadioGroup frequencyGroup = dialogView.findViewById(R.id.frequency_group);
        LinearLayout customIntervalLayout = dialogView.findViewById(R.id.custom_interval_layout);
        EditText customIntervalInput = dialogView.findViewById(R.id.custom_interval_input);
        TimePicker timePicker = dialogView.findViewById(R.id.time_picker);

        timePicker.setIs24HourView(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.setHour(stats.reminderHour);
            timePicker.setMinute(stats.reminderMinute);
        } else {
            timePicker.setCurrentHour(stats.reminderHour);
            timePicker.setCurrentMinute(stats.reminderMinute);
        }

        frequencyGroup.setOnCheckedChangeListener((group, checkedId) -> {
            customIntervalLayout.setVisibility(checkedId == R.id.custom_interval ? View.VISIBLE : View.GONE);
            dialogView.findViewById(R.id.start_date_layout).setVisibility(
                    checkedId == R.id.every_minute || checkedId == R.id.hourly ? View.GONE : View.VISIBLE);
        });

        if (stats.reminderInterval == -60) {
            frequencyGroup.check(R.id.hourly);
        } else if (stats.reminderInterval == 1) {
            frequencyGroup.check(R.id.daily);
        } else if (stats.reminderInterval == 2) {
            frequencyGroup.check(R.id.every_other_day);
        } else if (stats.reminderInterval == 7) {
            frequencyGroup.check(R.id.weekly);
        } else if (stats.reminderInterval == 30) {
            frequencyGroup.check(R.id.monthly);
        } else {
            frequencyGroup.check(R.id.custom_interval);
            customIntervalInput.setText(String.valueOf(Math.abs(stats.reminderInterval)));
        }

        builder.setView(dialogView);
        builder.setPositiveButton(getString(R.string.save_button), (dialog, which) -> {
            int newHour = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? timePicker.getHour() : timePicker.getCurrentHour();
            int newMinute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? timePicker.getMinute() : timePicker.getCurrentMinute();
            int selectedId = frequencyGroup.getCheckedRadioButtonId();

            if (selectedId == R.id.hourly) {
                stats.reminderInterval = -60; // Every hour
            } else if (selectedId == R.id.daily) {
                stats.reminderInterval = 1; // Daily
            } else if (selectedId == R.id.every_other_day) {
                stats.reminderInterval = 2; // Every other day
            } else if (selectedId == R.id.weekly) {
                stats.reminderInterval = 7; // Weekly
            } else if (selectedId == R.id.monthly) {
                stats.reminderInterval = 30; // Monthly
            } else {
                try {
                    int minutes = Integer.parseInt(customIntervalInput.getText().toString());
                    stats.reminderInterval = minutes > 0 ? -minutes : -1; // Negative for minutes
                } catch (NumberFormatException e) {
                    stats.reminderInterval = -1; // Default to every minute
                }
            }

            saveHabitReminderTime(key, newHour, newMinute, stats);
            displayData();
            Toast.makeText(this, getString(R.string.reminder_set_toast) + " " + dateFormat.format(stats.nextReminder), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(getString(R.string.cancel_button), null);
        builder.show();
    }

    private void saveHabitReminderTime(String key, int hour, int minute, HabitStats stats) {
        stats.reminderHour = hour;
        stats.reminderMinute = minute;

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        // Adjust next reminder time based on interval
        if (stats.reminderInterval < 0) {
            // Minute-based interval
            int minutes = Math.abs(stats.reminderInterval);
            if (next.getTimeInMillis() <= System.currentTimeMillis()) {
                long currentTime = System.currentTimeMillis();
                long intervalMillis = minutes * 60 * 1000L;
                long timeDiff = currentTime - next.getTimeInMillis();
                long intervalsToAdd = (timeDiff / intervalMillis) + 1;
                next.add(Calendar.MINUTE, (int) (intervalsToAdd * minutes));
            }
        } else {
            // Day-based interval
            if (next.getTimeInMillis() <= System.currentTimeMillis()) {
                next.add(Calendar.DAY_OF_YEAR, stats.reminderInterval);
            }
        }

        stats.nextReminder = next.getTime();

        SharedPreferences.Editor editor = statsPrefs.edit();
        editor.putInt(key + "_reminder_hour", hour);
        editor.putInt(key + "_reminder_minute", minute);
        editor.putString(key + "_next_reminder", dateFormat.format(stats.nextReminder));
        editor.putInt(key + "_reminder_interval", stats.reminderInterval);
        editor.apply();

        if (currentUser != null) {
            saveStatsToFirebase(currentUser.getUid(), key, stats, true);
        }

        cancelNotification(key);
        scheduleNotification(key, true, stats.nextReminder);
    }

    private void showRelapseDialog(String key, HabitStats stats) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.relapse_dialog_title))
                .setMessage(getString(R.string.relapse_confirmation_message))
                .setPositiveButton(getString(R.string.yes_button), (dialog, which) -> showDatePickerDialog(key, stats))
                .setNegativeButton(getString(R.string.no_button), null)
                .show();
    }

    private void showDatePickerDialog(String key, HabitStats stats) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, day);
                    if (selectedDate.getTimeInMillis() > System.currentTimeMillis()) {
                        Toast.makeText(this, getString(R.string.future_date_error), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showTimePickerDialog(key, stats, selectedDate);
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private boolean hasRelapseToday(HabitStats stats) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String today = fmt.format(new Date());
        return stats.relapseDates.stream().anyMatch(date -> fmt.format(date).equals(today));
    }

    private boolean isTodayMarked(HabitStats stats) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String today = fmt.format(new Date());
        return stats.markedDays.stream().anyMatch(date -> fmt.format(date).equals(today));
    }

    private void showDeleteConfirmationDialog(String key, boolean isHabit) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_confirmation_title))
                .setMessage(getString(R.string.delete_confirmation_message, getTextForKey(key)))
                .setPositiveButton(getString(R.string.yes_button), (dialog, which) -> {
                    if (isHabit) {
                        habitKeys.remove(key);
                        habitStats.remove(key);
                        sharedPrefs.edit().putStringSet("selectedHabits", habitKeys).apply();
                    } else {
                        addictionKeys.remove(key);
                        addictionStats.remove(key);
                        sharedPrefs.edit().putStringSet("selectedAddictions", addictionKeys).apply();
                    }

                    SharedPreferences.Editor editor = statsPrefs.edit();
                    editor.remove(key + "_start_date");
                    editor.remove(key + "_streak");
                    editor.remove(key + "_longest_streak");
                    editor.remove(key + "_progress");
                    editor.remove(key + "_next_reminder");
                    editor.apply();

                    if (currentUser != null) {
                        database.child("users").child(currentUser.getUid())
                                .child(isHabit ? "habit_stats" : "addiction_stats").child(key).removeValue();
                        saveUserDataToFirebase(currentUser.getUid());
                    }

                    displayData();
                    Toast.makeText(this, getString(R.string.item_deleted_toast), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private LinearLayout createSleepTimeLayout() {
        LinearLayout sleepTimeLayout = new LinearLayout(this);
        sleepTimeLayout.setOrientation(LinearLayout.VERTICAL);

        TextView sleepStartLabel = new TextView(this);
        sleepStartLabel.setText(getString(R.string.sleep_start_label));
        sleepStartLabel.setTextSize(16);
        sleepTimeLayout.addView(sleepStartLabel);

        Button sleepStartButton = new Button(this);
        sleepStartButton.setText(String.format(Locale.getDefault(), "%02d:%02d", sleepStartHour, sleepStartMinute));
        sleepStartButton.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            sleepStartHour = hour;
            sleepStartMinute = minute;
            sleepStartButton.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
            notifPrefs.edit()
                    .putInt("sleep_start_hour", hour)
                    .putInt("sleep_start_minute", minute)
                    .apply();
            rescheduleAllNotifications();
        }, sleepStartHour, sleepStartMinute, true).show());
        sleepTimeLayout.addView(sleepStartButton);

        TextView sleepEndLabel = new TextView(this);
        sleepEndLabel.setText(getString(R.string.sleep_end_label));
        sleepEndLabel.setTextSize(16);
        sleepTimeLayout.addView(sleepEndLabel);

        Button sleepEndButton = new Button(this);
        sleepEndButton.setText(String.format(Locale.getDefault(), "%02d:%02d", sleepEndHour, sleepEndMinute));
        sleepEndButton.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            sleepEndHour = hour;
            sleepEndMinute = minute;
            sleepEndButton.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
            notifPrefs.edit()
                    .putInt("sleep_end_hour", hour)
                    .putInt("sleep_end_minute", minute)
                    .apply();
            rescheduleAllNotifications();
        }, sleepEndHour, sleepEndMinute, true).show());
        sleepTimeLayout.addView(sleepEndButton);

        return sleepTimeLayout;
    }

    private void updateSleepTimeLayoutVisibility(LinearLayout notificationsLayout, boolean isVisible) {
        for (int i = 0; i < notificationsLayout.getChildCount(); i++) {
            View child = notificationsLayout.getChildAt(i);
            if (child instanceof LinearLayout && child != notificationsLayout.getChildAt(0)) {
                child.setVisibility(isVisible ? View.VISIBLE : View.GONE);
                break;
            }
        }
    }

    private void rescheduleAllNotifications() {
        for (String key : habitKeys) {
            HabitStats stats = habitStats.get(key);
            if (stats != null && stats.nextReminder != null) {
                cancelNotification(key);
                scheduleNotification(key, true, stats.nextReminder);
            }
        }
        cancelNotification("all_addictions");
        if (addictionRemindersEnabled) {
            updateAddictionReminders();
        }
    }

    private void updateAddictionReminders() {
        if (addictionKeys.isEmpty()) return;
        cancelNotification("all_addictions");

        Calendar reminderTime = Calendar.getInstance();
        reminderTime.set(Calendar.HOUR_OF_DAY, addictionReminderHour);
        reminderTime.set(Calendar.MINUTE, addictionReminderMinute);
        reminderTime.set(Calendar.SECOND, 0);
        reminderTime.set(Calendar.MILLISECOND, 0);
        if (reminderTime.getTimeInMillis() <= System.currentTimeMillis()) {
            reminderTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        scheduleNotification("all_addictions", false, reminderTime.getTime());
        Toast.makeText(this, getString(R.string.addiction_reminders_updated_toast), Toast.LENGTH_SHORT).show();
    }

    private void cancelNotification(String key) {
        HabitNotificationScheduler.cancelScheduledNotification(this, key);
    }

    private void scheduleAllHabitReminders() {
        for (String key : habitKeys) {
            HabitStats stats = habitStats.get(key);
            if (stats == null) {
                // Create default stats if missing
                stats = new HabitStats();
                stats.startDate = new Date();
                stats.lastCheckDate = new Date();
                stats.nextReminder = getDefaultReminderTime();
                stats.reminderHour = 18;
                stats.reminderMinute = 0;
                stats.reminderInterval = 1; // Default: daily
                habitStats.put(key, stats);
                Log.d(TAG, "Created default stats for key: " + key);
            }

            // Adjust nextReminder if it's in the past
            Calendar next = Calendar.getInstance();
            next.setTime(stats.nextReminder != null ? stats.nextReminder : getDefaultReminderTime());

            while (next.getTimeInMillis() <= System.currentTimeMillis()) {
                if (stats.reminderInterval < 0) {
                    // Minute-based interval
                    int minutes = Math.abs(stats.reminderInterval);
                    next.add(Calendar.MINUTE, minutes);
                } else {
                    // Day-based interval
                    next.add(Calendar.DAY_OF_YEAR, stats.reminderInterval > 0 ? stats.reminderInterval : 1);
                }
                Log.d(TAG, "Adjusted past nextReminder for key: " + key + " to " + dateFormat.format(next.getTime()));
            }

            // Update stats with the new nextReminder
            stats.nextReminder = next.getTime();
            stats.reminderHour = next.get(Calendar.HOUR_OF_DAY);
            stats.reminderMinute = next.get(Calendar.MINUTE);

            // Save updated stats
            saveStatsToPreferences(key, stats);
            if (currentUser != null) {
                saveStatsToFirebase(currentUser.getUid(), key, stats, true);
            }

            // Schedule the notification
            cancelNotification(key); // Cancel any existing notification
            scheduleNotification(key, true, stats.nextReminder);
            Log.d(TAG, "Scheduled notification for key: " + key + " at " + dateFormat.format(stats.nextReminder));
        }
    }

    private Date getDefaultReminderTime() {
        Calendar defaultTime = Calendar.getInstance();
        defaultTime.set(Calendar.HOUR_OF_DAY, 18);
        defaultTime.set(Calendar.MINUTE, 0);
        defaultTime.set(Calendar.SECOND, 0);
        defaultTime.set(Calendar.MILLISECOND, 0);

        if (defaultTime.getTimeInMillis() <= System.currentTimeMillis()) {
            defaultTime.add(Calendar.DAY_OF_YEAR, 1);
        }
        return defaultTime.getTime();
    }
    private void scheduleNotification(String key, boolean isHabit, Date reminderTime) {
        String title = isHabit ? getString(R.string.habit_reminder_title, getTextForKey(key)) : getString(R.string.addiction_reminder_title);
        String message = isHabit ? getString(R.string.habit_reminder_message) : getString(R.string.addiction_reminder_message);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(reminderTime);
        if (sleepModeEnabled && isInSleepTime(reminderTime)) {
            calendar.set(Calendar.HOUR_OF_DAY, sleepEndHour);
            calendar.set(Calendar.MINUTE, sleepEndMinute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        }

        int reminderInterval = isHabit ? Objects.requireNonNull(habitStats.get(key)).reminderInterval : 1; // Use habit's reminderInterval
        HabitNotificationScheduler.scheduleHabitNotification(
                this, key, title, calendar.getTime(), isHabit, message, reminderInterval);
    }

    private boolean isInSleepTime(Date time) {
        if (!sleepModeEnabled) return false;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(time);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        if (sleepStartHour < sleepEndHour) {
            return (hour > sleepStartHour || (hour == sleepStartHour && minute >= sleepStartMinute)) &&
                    (hour < sleepEndHour || (hour == sleepEndHour && minute < sleepEndMinute));
        } else {
            return (hour > sleepStartHour || (hour == sleepStartHour && minute >= sleepStartMinute)) ||
                    (hour < sleepEndHour || (hour == sleepEndHour && minute < sleepEndMinute));
        }
    }

    private String getTextForKey(String key) {
        if (key == null || key.isEmpty()) return "";
        switch (key) {
            case "healthy_sleep": return getString(R.string.healthy_sleep);
            case "morning_exercises": return getString(R.string.morning_exercises);
            case "reading": return getString(R.string.reading);
            case "meditation": return getString(R.string.meditation);
            case "alcohol": return getString(R.string.alcohol);
            case "cigarettes": return getString(R.string.cigarettes);
            case "drugs": return getString(R.string.drugs);
            case "gambling": return getString(R.string.gambling);
            case "self_harm": return getString(R.string.self_harm);
            case "pornography": return getString(R.string.pornography);
            case "social_networks": return getString(R.string.social_networks);
            case "sugar": return getString(R.string.sugar);
            default: return sharedPrefs.getString(key, key);
        }
    }

    private void saveUserDataToFirebase(String userId) {
        DatabaseReference userRef = database.child("users").child(userId);
        userRef.child("habits").setValue(new ArrayList<>(habitKeys));
        userRef.child("addictions").setValue(new ArrayList<>(addictionKeys));

        Map<String, Object> habitLabels = new HashMap<>();
        for (String key : habitKeys) {
            if (key.startsWith("custom_habit_")) {
                habitLabels.put(key, getTextForKey(key));
            }
        }
        if (!habitLabels.isEmpty()) {
            userRef.child("habit_labels").setValue(habitLabels);
        }

        Map<String, Object> addictionLabels = new HashMap<>();
        for (String key : addictionKeys) {
            if (key.startsWith("custom_addiction_")) {
                addictionLabels.put(key, getTextForKey(key));
            }
        }
        if (!addictionLabels.isEmpty()) {
            userRef.child("addiction_labels").setValue(addictionLabels);
        }
    }

    private void showNotificationPermissionExplanation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.notification_permission_title))
                .setMessage(getString(R.string.notification_permission_message))
                .setPositiveButton(getString(R.string.settings_button), (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.later_button), (dialog, which) -> Toast.makeText(this, getString(R.string.no_notifications_warning), Toast.LENGTH_LONG).show())
                .show();
    }

    private void showLoadingIndicator(boolean show) {
        homeContainer.removeAllViews();
        if (show) {
            TextView loadingText = new TextView(this);
            loadingText.setText(getString(R.string.loading_text));
            loadingText.setTextSize(18);
            loadingText.setPadding(32, 32, 32, 32);
            homeContainer.addView(loadingText);
        }
    }

    private void showHomeScreen() {
        homeContainer.setVisibility(View.VISIBLE);
        habitsContainer.setVisibility(View.GONE);
        addictionsContainer.setVisibility(View.GONE);
        messagesContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
    }

    private void showHabitsScreen() {
        homeContainer.setVisibility(View.GONE);
        habitsContainer.setVisibility(View.VISIBLE);
        addictionsContainer.setVisibility(View.GONE);
        messagesContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
    }

    private void showAddictionsScreen() {
        homeContainer.setVisibility(View.GONE);
        habitsContainer.setVisibility(View.GONE);
        addictionsContainer.setVisibility(View.VISIBLE);
        messagesContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
    }

    private Date getTomorrow() {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        return tomorrow.getTime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isDataLoaded) displayData();
        startNotificationService();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    
    private void initializeUI() {
        homeContainer = findViewById(R.id.home_container);
        habitsContainer = findViewById(R.id.habits_container);
        addictionsContainer = findViewById(R.id.addictions_container);
        messagesContainer = findViewById(R.id.messages_container);
        settingsContainer = findViewById(R.id.settings_container);
        habitsListContainer = findViewById(R.id.habits_list_container);
        addictionsListContainer = findViewById(R.id.addictions_list_container);
        showHomeScreen();
        checkAndPromptForUsername(); // Prompt for username on app start
    }

    private void showUsernameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.set_username_title));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText usernameInput = new EditText(this);
        usernameInput.setHint(getString(R.string.username_hint));
        layout.addView(usernameInput);

        builder.setView(layout);
        builder.setPositiveButton(getString(R.string.save_button), null); // Set null to override click behavior
        builder.setNegativeButton(getString(R.string.cancel_button), null);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            if (username.isEmpty()) {
                usernameInput.setError(getString(R.string.username_empty_error));
                return;
            }

            // Check for username uniqueness
            database.child("usernames").child(username).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        usernameInput.setError(getString(R.string.username_taken_error));
                    } else {
                        // Save username to Firebase
                        String userId = currentUser.getUid();
                        database.child("users").child(userId).child("username").setValue(username);
                        database.child("usernames").child(username).setValue(userId);
                        Toast.makeText(ResultsActivity.this, getString(R.string.username_set_toast), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error checking username availability: " + error.getMessage());
                    Toast.makeText(ResultsActivity.this, getString(R.string.firebase_load_error), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Data
    private Map<String, Goal> goals = new HashMap<>();

    // Goal class definition
    public static class Goal {
        public String habitKey; // Associated habit or addiction key
        public String type; // "streak" or "progress"
        public int target; // Target value (days for streak, percentage for progress)
        public boolean achieved; // Whether the goal is achieved
        public Date setDate; // Date the goal was set
    }

    private void loadDataFromFirebase(String userId) {
        showLoadingIndicator(true);
        database.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> newHabitKeys = new HashSet<>();
                Set<String> newAddictionKeys = new HashSet<>();
                Map<String, String> habitLabels = new HashMap<>();
                Map<String, String> addictionLabels = new HashMap<>();
                Map<String, Goal> newGoals = new HashMap<>();

                // Load habits
                if (snapshot.child("habits").exists()) {
                    for (DataSnapshot habitSnapshot : snapshot.child("habits").getChildren()) {
                        String key = habitSnapshot.getValue(String.class);
                        if (key != null) newHabitKeys.add(key);
                    }
                }

                // Load addictions
                if (snapshot.child("addictions").exists()) {
                    for (DataSnapshot addictionSnapshot : snapshot.child("addictions").getChildren()) {
                        String key = addictionSnapshot.getValue(String.class);
                        if (key != null) newAddictionKeys.add(key);
                    }
                }

                // Load labels
                if (snapshot.child("habit_labels").exists()) {
                    for (DataSnapshot labelSnapshot : snapshot.child("habit_labels").getChildren()) {
                        String key = labelSnapshot.getKey();
                        String value = labelSnapshot.getValue(String.class);
                        if (key != null && value != null) habitLabels.put(key, value);
                    }
                }
                if (snapshot.child("addiction_labels").exists()) {
                    for (DataSnapshot labelSnapshot : snapshot.child("addiction_labels").getChildren()) {
                        String key = labelSnapshot.getKey();
                        String value = labelSnapshot.getValue(String.class);
                        if (key != null && value != null) addictionLabels.put(key, value);
                    }
                }

                // Load goals
                if (snapshot.child("goals").exists()) {
                    for (DataSnapshot goalSnapshot : snapshot.child("goals").getChildren()) {
                        String goalId = goalSnapshot.getKey();
                        Goal goal = parseGoalFromFirebase(goalSnapshot);
                        if (goalId != null && goal != null) newGoals.put(goalId, goal);
                    }
                }

                habitKeys = newHabitKeys;
                addictionKeys = newAddictionKeys;
                goals = newGoals;
                loadStatsFromFirebase(snapshot);
                saveKeysToSharedPreferences(newHabitKeys, newAddictionKeys, habitLabels, addictionLabels, newGoals);
                displayData();
                isDataLoaded = true;
                showLoadingIndicator(false);
                scheduleAllHabitReminders();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase load error: " + error.getMessage());
                Toast.makeText(ResultsActivity.this, getString(R.string.firebase_load_error), Toast.LENGTH_SHORT).show();
                loadDataFromLocalStorage();
            }
        });
    }

    private void loadDataFromLocalStorage() {
        habitKeys = sharedPrefs.getStringSet("selectedHabits", new HashSet<>());
        addictionKeys = sharedPrefs.getStringSet("selectedAddictions", new HashSet<>());
        loadStatsFromPreferences();
        loadGoalsFromPreferences();
        displayData();
        isDataLoaded = true;
        showLoadingIndicator(false);
        scheduleAllHabitReminders();
    }

    private Goal parseGoalFromFirebase(DataSnapshot snapshot) {
        Goal goal = new Goal();
        try {
            goal.habitKey = snapshot.child("habitKey").getValue(String.class);
            goal.type = snapshot.child("type").getValue(String.class);
            Long target = snapshot.child("target").getValue(Long.class);
            goal.target = target != null ? target.intValue() : 0;
            goal.achieved = snapshot.child("achieved").getValue(Boolean.class) != null &&
                    Boolean.TRUE.equals(snapshot.child("achieved").getValue(Boolean.class));
            String setDateStr = snapshot.child("setDate").getValue(String.class);
            goal.setDate = setDateStr != null ? dateFormat.parse(setDateStr) : new Date();
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing goal: " + e.getMessage());
            return null;
        }
        return goal;
    }

    private void loadGoalsFromPreferences() {
        goals.clear();
        Map<String, ?> allPrefs = goalsPrefs.getAll();
        Set<String> goalIds = new HashSet<>();
        for (String key : allPrefs.keySet()) {
            if (key.endsWith("_habitKey")) {
                goalIds.add(key.replace("_habitKey", ""));
            }
        }

        for (String goalId : goalIds) {
            Goal goal = new Goal();
            goal.habitKey = goalsPrefs.getString(goalId + "_habitKey", "");
            goal.type = goalsPrefs.getString(goalId + "_type", "");
            goal.target = goalsPrefs.getInt(goalId + "_target", 0);
            goal.achieved = goalsPrefs.getBoolean(goalId + "_achieved", false);
            try {
                String setDateStr = goalsPrefs.getString(goalId + "_setDate", null);
                goal.setDate = setDateStr != null ? dateFormat.parse(setDateStr) : new Date();
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing goal set date: " + e.getMessage());
                goal.setDate = new Date();
            }
            goals.put(goalId, goal);
        }
    }

    private void saveGoalToPreferences(String goalId, Goal goal) {
        SharedPreferences.Editor editor = goalsPrefs.edit();
        editor.putString(goalId + "_habitKey", goal.habitKey);
        editor.putString(goalId + "_type", goal.type);
        editor.putInt(goalId + "_target", goal.target);
        editor.putBoolean(goalId + "_achieved", goal.achieved);
        editor.putString(goalId + "_setDate", dateFormat.format(goal.setDate));
        editor.apply();
    }

    private void saveGoalToFirebase(String userId, String goalId, Goal goal) {
        DatabaseReference goalRef = database.child("users").child(userId).child("goals").child(goalId);
        Map<String, Object> goalMap = new HashMap<>();
        goalMap.put("habitKey", goal.habitKey);
        goalMap.put("type", goal.type);
        goalMap.put("target", goal.target);
        goalMap.put("achieved", goal.achieved);
        goalMap.put("setDate", dateFormat.format(goal.setDate));
        goalRef.setValue(goalMap);
    }

    private void showAddGoalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.add_goal_dialog_title));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        // Spinner for selecting habit or addiction
        TextView habitLabel = new TextView(this);
        habitLabel.setText(getString(R.string.select_habit_label));
        habitLabel.setPadding(0, 0, 0, 8);
        layout.addView(habitLabel);

        Spinner habitSpinner = new Spinner(this);
        List<String> habitOptions = new ArrayList<>();
        Map<String, String> habitKeyMap = new HashMap<>();
        habitOptions.add(getString(R.string.select_habit_placeholder));

        for (String key : habitKeys) {
            habitOptions.add(getTextForKey(key));
            habitKeyMap.put(getTextForKey(key), key);
        }
        for (String key : addictionKeys) {
            habitOptions.add(getTextForKey(key));
            habitKeyMap.put(getTextForKey(key), key);
        }

        ArrayAdapter<String> habitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, habitOptions);
        habitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        habitSpinner.setAdapter(habitAdapter);
        layout.addView(habitSpinner);

        // Goal type selection
        TextView typeLabel = new TextView(this);
        typeLabel.setText(getString(R.string.goal_type_label));
        typeLabel.setPadding(0, 16, 0, 8);
        layout.addView(typeLabel);

        RadioGroup typeGroup = new RadioGroup(this);
        RadioButton streakRadio = new RadioButton(this);
        streakRadio.setText(getString(R.string.streak_goal));
        streakRadio.setId(View.generateViewId());
        typeGroup.addView(streakRadio);

        RadioButton progressRadio = new RadioButton(this);
        progressRadio.setText(getString(R.string.progress_goal));
        progressRadio.setId(View.generateViewId());
        typeGroup.addView(progressRadio);
        streakRadio.setChecked(true);
        layout.addView(typeGroup);

        // Target value input
        TextView targetLabel = new TextView(this);
        targetLabel.setText(getString(R.string.target_value_label));
        targetLabel.setPadding(0, 16, 0, 8);
        layout.addView(targetLabel);

        EditText targetInput = new EditText(this);
        targetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        targetInput.setHint(getString(R.string.target_value_hint));
        layout.addView(targetInput);

        builder.setView(layout);
        builder.setPositiveButton(getString(R.string.add_button), (dialog, which) -> {
            String selectedHabit = habitSpinner.getSelectedItem().toString();
            if (selectedHabit.equals(getString(R.string.select_habit_placeholder))) {
                Toast.makeText(this, getString(R.string.select_habit_error), Toast.LENGTH_SHORT).show();
                return;
            }

            String targetStr = targetInput.getText().toString().trim();
            if (targetStr.isEmpty()) {
                Toast.makeText(this, getString(R.string.target_empty_error), Toast.LENGTH_SHORT).show();
                return;
            }

            int target;
            try {
                target = Integer.parseInt(targetStr);
                if (target <= 0) {
                    Toast.makeText(this, getString(R.string.target_invalid_error), Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.target_invalid_error), Toast.LENGTH_SHORT).show();
                return;
            }

            String habitKey = habitKeyMap.get(selectedHabit);
            String goalType = typeGroup.getCheckedRadioButtonId() == streakRadio.getId() ? "streak" : "progress";
            String goalId = "goal_" + UUID.randomUUID().toString().substring(0, 8);

            Goal goal = new Goal();
            goal.habitKey = habitKey;
            goal.type = goalType;
            goal.target = target;
            goal.achieved = false;
            goal.setDate = new Date();

            goals.put(goalId, goal);
            saveGoalToPreferences(goalId, goal);
            if (currentUser != null) {
                saveGoalToFirebase(currentUser.getUid(), goalId, goal);
            }

            checkAndUpdateGoalStatus(goalId, goal);
            displayData();
            Toast.makeText(this, getString(R.string.goal_added_toast), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(getString(R.string.cancel_button), null);
        builder.show();
    }

    private void showDeleteGoalConfirmationDialog(String goalId) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_goal_confirmation_title))
                .setMessage(getString(R.string.delete_goal_confirmation_message))
                .setPositiveButton(getString(R.string.yes_button), (dialog, which) -> {
                    goals.remove(goalId);
                    SharedPreferences.Editor editor = goalsPrefs.edit();
                    editor.remove(goalId + "_habitKey");
                    editor.remove(goalId + "_type");
                    editor.remove(goalId + "_target");
                    editor.remove(goalId + "_achieved");
                    editor.remove(goalId + "_setDate");
                    editor.apply();

                    if (currentUser != null) {
                        database.child("users").child(currentUser.getUid()).child("goals").child(goalId).removeValue();
                    }

                    displayData();
                    Toast.makeText(this, getString(R.string.goal_deleted_toast), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void setupAddButton() {
        FloatingActionButton addButton = findViewById(R.id.fab_add);

        addButton.setOnClickListener(v -> showAddGoalDialog());

        if (currentUser != null) {
            // Check if the user is Admin
            DatabaseReference userRef = database.child("users").child(currentUser.getUid()).child("username");
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String username = snapshot.getValue(String.class);
                    boolean isAdmin = "Admin".equalsIgnoreCase(username);

                    // Show the button based on the active screen
                    BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
                    bottomNav.setOnNavigationItemSelectedListener(item -> {
                        int itemId = item.getItemId();
                        if (itemId == R.id.navigation_home) {
                            showHomeScreen();
                            addButton.setVisibility(View.VISIBLE); // Show FAB on home screen
                            addButton.setOnClickListener(v -> showAddGoalDialog()); // Add goal
                            return true;
                        } else if (itemId == R.id.navigation_habits) {
                            showHabitsScreen();
                            addButton.setVisibility(View.VISIBLE);
                            addButton.setOnClickListener(v -> showAddItemDialog()); // Add habit
                            return true;
                        } else if (itemId == R.id.navigation_addictions) {
                            showAddictionsScreen();
                            addButton.setVisibility(View.VISIBLE);
                            addButton.setOnClickListener(v -> showAddItemDialog()); // Add addiction
                            return true;
                        } else if (itemId == R.id.navigation_messages) {
                            showMessagesScreen();
                            addButton.setVisibility(isAdmin ? View.VISIBLE : View.GONE); // Show only for Admin
                            addButton.setOnClickListener(v -> showAddItemDialog()); // Add chat
                            return true;
                        } else if (itemId == R.id.navigation_settings) {
                            showSettingsScreen();
                            addButton.setVisibility(View.GONE);
                            return true;
                        }
                        return false;
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error checking username: " + error.getMessage());
                    Toast.makeText(ResultsActivity.this, getString(R.string.firebase_load_error), Toast.LENGTH_SHORT).show();
                    addButton.setVisibility(View.GONE);
                }
            });
        }
    }

    private void createHomeScreen() {
        Handler handler = new Handler(Looper.getMainLooper());
        int currentDelay = 0;

        handler.postDelayed(() -> {
            homeContainer.removeAllViews();

            TextView welcomeText = new TextView(this);
            welcomeText.setText(getString(R.string.welcome_text));
            welcomeText.setTextSize(24);
            welcomeText.setPadding(32, 32, 32, 16);
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
            welcomeText.setTextColor(ContextCompat.getColor(this, isDarkTheme ? R.color.teal_200 : R.color.purple_500));
            homeContainer.addView(welcomeText);

            TextView habitsStatsTitle = new TextView(this);
            habitsStatsTitle.setText(getString(R.string.habits_stats_title));
            habitsStatsTitle.setTextSize(18);
            habitsStatsTitle.setPadding(32, 16, 32, 8);
            homeContainer.addView(habitsStatsTitle);

            if (!habitKeys.isEmpty()) {
                CardView habitsCard = createStatsCard(
                        getString(R.string.overall_progress) + " " + getAverageProgress(habitStats) + "%",
                        getString(R.string.best_habit) + " " + getTextForKey(getBestHabit(habitKeys, habitStats))
                );
                homeContainer.addView(habitsCard);
            } else {
                TextView noHabitsText = new TextView(this);
                noHabitsText.setText(getString(R.string.no_habits_text));
                noHabitsText.setPadding(32, 8, 32, 16);
                homeContainer.addView(noHabitsText);
            }

            TextView addictionsStatsTitle = new TextView(this);
            addictionsStatsTitle.setText(getString(R.string.addiction_stats_title));
            addictionsStatsTitle.setTextSize(18);
            addictionsStatsTitle.setPadding(32, 16, 32, 8);
            homeContainer.addView(addictionsStatsTitle);

            if (!addictionKeys.isEmpty()) {
                CardView addictionsCard = createStatsCard(
                        getString(R.string.overall_progress) + " " + getAverageProgress(addictionStats) + "%",
                        getString(R.string.best_struggle) + " " + getTextForKey(getBestHabit(addictionKeys, addictionStats))
                );
                homeContainer.addView(addictionsCard);
            } else {
                TextView noAddictionsText = new TextView(this);
                noAddictionsText.setText(getString(R.string.no_addictions_text));
                noAddictionsText.setPadding(32, 8, 32, 16);
                homeContainer.addView(noAddictionsText);
            }

            CardView motivationCard = new CardView(this);
            motivationCard.setCardElevation(8);
            motivationCard.setRadius(16);
            motivationCard.setUseCompatPadding(true);
            motivationCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.purple_light));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(32, 16, 32, 32);
            motivationCard.setLayoutParams(params);

            String[] quotes = {
                    getString(R.string.motivation_quote_1),
                    getString(R.string.motivation_quote_2),
                    getString(R.string.motivation_quote_3),
                    getString(R.string.motivation_quote_4),
                    getString(R.string.motivation_quote_5)
            };

            TextView motivationText = new TextView(this);
            motivationText.setText(quotes[new Random().nextInt(quotes.length)]);
            motivationText.setTextColor(Color.WHITE);
            motivationText.setTextSize(16);
            motivationText.setPadding(24, 24, 24, 24);
            motivationCard.addView(motivationText);
            homeContainer.addView(motivationCard);

            // Add Goals Section
            TextView goalsTitle = new TextView(this);
            goalsTitle.setText(getString(R.string.goals_title));
            goalsTitle.setTextSize(18);
            goalsTitle.setPadding(32, 16, 32, 8);
            homeContainer.addView(goalsTitle);

            if (!goals.isEmpty()) {
                for (Map.Entry<String, Goal> entry : goals.entrySet()) {
                    String goalId = entry.getKey();
                    Goal goal = entry.getValue();
                    checkAndUpdateGoalStatus(goalId, goal);

                    CardView goalCard = new CardView(this);
                    goalCard.setCardElevation(8);
                    goalCard.setRadius(16);
                    goalCard.setUseCompatPadding(true);
                    LinearLayout.LayoutParams goalParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    goalParams.setMargins(32, 8, 32, 8);
                    goalCard.setLayoutParams(goalParams);

                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(16, 16, 16, 16);
                    goalCard.addView(layout);

                    TextView goalText = new TextView(this);
                    String goalDescription = String.format(
                            getString(R.string.goal_description),
                            getTextForKey(goal.habitKey),
                            goal.type.equals("streak") ? goal.target + " " + getString(R.string.days_label) :
                                    goal.target + "%",
                            goal.achieved ? getString(R.string.achieved_label) : getString(R.string.in_progress_label)
                    );
                    goalText.setText(goalDescription);
                    goalText.setTextColor(ContextCompat.getColor(this, goal.achieved ? R.color.green : R.color.black));
                    layout.addView(goalText);

                    TextView setDateText = new TextView(this);
                    setDateText.setText(getString(R.string.set_date_label) + " " +
                            new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(goal.setDate));
                    setDateText.setPadding(0, 4, 0, 0);
                    layout.addView(setDateText);

                    Button deleteGoalButton = new Button(this);
                    deleteGoalButton.setText(getString(R.string.delete_goal_button));
                    deleteGoalButton.setOnClickListener(v -> showDeleteGoalConfirmationDialog(goalId));
                    layout.addView(deleteGoalButton);

                    homeContainer.addView(goalCard);
                }
            } else {
                TextView noGoalsText = new TextView(this);
                noGoalsText.setText(getString(R.string.no_goals_text));
                noGoalsText.setPadding(32, 8, 32, 16);
                homeContainer.addView(noGoalsText);
            }

        }, currentDelay);
    }

    private void checkAndPromptForUsername() {
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        DatabaseReference userRef = database.child("users").child(userId).child("username");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String username = snapshot.getValue(String.class);
                if (username == null || username.isEmpty()) {
                    showUsernameDialog();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking username: " + error.getMessage());
                Toast.makeText(ResultsActivity.this, getString(R.string.firebase_load_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddItemDialog() {
        // Check if the Messages tab is active
        if (messagesContainer.getVisibility() == View.VISIBLE) {
            if (currentUser == null) {
                Toast.makeText(this, getString(R.string.user_not_logged_in_error), Toast.LENGTH_SHORT).show();
                return;
            }

            // Show dialog for creating a new chat
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.create_chat_dialog_title));

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(32, 16, 32, 16);

            EditText chatNameInput = new EditText(this);
            chatNameInput.setHint(getString(R.string.chat_name_hint));
            layout.addView(chatNameInput);

            // Gender restriction spinner
            TextView genderLabel = new TextView(this);
            genderLabel.setText(getString(R.string.gender_restriction_label));
            genderLabel.setPadding(0, 16, 0, 8);
            layout.addView(genderLabel);

            Spinner genderSpinner = new Spinner(this);
            String[] genderOptions = {
                    getString(R.string.gender_any),
                    getString(R.string.gender_male),
                    getString(R.string.gender_female),
                    getString(R.string.gender_other)
            };
            ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genderOptions);
            genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            genderSpinner.setAdapter(genderAdapter);
            layout.addView(genderSpinner);

            // Age range inputs
            TextView ageRangeLabel = new TextView(this);
            ageRangeLabel.setText(getString(R.string.age_range_label));
            ageRangeLabel.setPadding(0, 16, 0, 8);
            layout.addView(ageRangeLabel);

            LinearLayout ageRangeLayout = new LinearLayout(this);
            ageRangeLayout.setOrientation(LinearLayout.HORIZONTAL);

            EditText minAgeInput = new EditText(this);
            minAgeInput.setHint(getString(R.string.min_age_hint));
            minAgeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            minAgeInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            ageRangeLayout.addView(minAgeInput);

            EditText maxAgeInput = new EditText(this);
            maxAgeInput.setHint(getString(R.string.max_age_hint));
            maxAgeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            maxAgeInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            ageRangeLayout.addView(maxAgeInput);

            layout.addView(ageRangeLayout);

            builder.setView(layout);
            builder.setPositiveButton(getString(R.string.create_button), (dialog, which) -> {
                String chatName = chatNameInput.getText().toString().trim();
                if (chatName.isEmpty()) {
                    Toast.makeText(this, getString(R.string.chat_name_empty_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                String selectedGender = genderSpinner.getSelectedItem().toString();
                String genderRestriction = selectedGender.equals(getString(R.string.gender_any)) ? "any" : selectedGender.toLowerCase(Locale.getDefault());

                Integer minAge = null;
                Integer maxAge = null;
                try {
                    String minAgeStr = minAgeInput.getText().toString().trim();
                    String maxAgeStr = maxAgeInput.getText().toString().trim();
                    if (!minAgeStr.isEmpty()) minAge = Integer.parseInt(minAgeStr);
                    if (!maxAgeStr.isEmpty()) maxAge = Integer.parseInt(maxAgeStr);

                    if (minAge != null && maxAge != null && minAge > maxAge) {
                        Toast.makeText(this, getString(R.string.invalid_age_range_error), Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getString(R.string.invalid_age_format_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                String userId = currentUser.getUid();
                // Check if the user is Admin
                DatabaseReference userRef = database.child("users").child(userId).child("username");
                Integer finalMinAge = minAge;
                Integer finalMaxAge = maxAge;
                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String username = snapshot.getValue(String.class);
                        boolean isAdmin = "Admin".equalsIgnoreCase(username);
                        String chatId = UUID.randomUUID().toString();
                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("name", chatName);
                        chatData.put("created_at", dateFormat.format(new Date()));
                        chatData.put("created_by", userId);
                        chatData.put("gender_restriction", genderRestriction);
                        if (finalMinAge != null) chatData.put("min_age", finalMinAge);
                        if (finalMaxAge != null) chatData.put("max_age", finalMaxAge);

                        if (isAdmin) {
                            // Admin creates global chat
                            DatabaseReference globalChatsRef = database.child("chats").child(chatId);
                            globalChatsRef.setValue(chatData).addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(ResultsActivity.this, getString(R.string.global_chat_created_toast), Toast.LENGTH_SHORT).show();
                                    showMessagesScreen();
                                } else {
                                    Toast.makeText(ResultsActivity.this, getString(R.string.firebase_save_error), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error checking username: " + error.getMessage());
                        Toast.makeText(ResultsActivity.this, getString(R.string.firebase_load_error), Toast.LENGTH_SHORT).show();
                    }
                });
            });
            builder.setNegativeButton(getString(R.string.cancel_button), null);
            builder.show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.add_item_dialog_title));

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(32, 16, 32, 16);

            RadioGroup typeGroup = new RadioGroup(this);
            RadioButton habitRadio = new RadioButton(this);
            habitRadio.setText(getString(R.string.habit_radio));
            habitRadio.setId(View.generateViewId());
            typeGroup.addView(habitRadio);

            RadioButton addictionRadio = new RadioButton(this);
            addictionRadio.setText(getString(R.string.addiction_radio));
            addictionRadio.setId(View.generateViewId());
            typeGroup.addView(addictionRadio);
            habitRadio.setChecked(true);
            layout.addView(typeGroup);

            String[][] habitsWithKeys = {
                    {"healthy_sleep", getString(R.string.healthy_sleep)},
                    {"morning_exercises", getString(R.string.morning_exercises)},
                    {"reading", getString(R.string.reading)},
                    {"meditation", getString(R.string.meditation)}
            };

            String[][] addictionsWithKeys = {
                    {"alcohol", getString(R.string.alcohol)},
                    {"cigarettes", getString(R.string.cigarettes)},
                    {"drugs", getString(R.string.drugs)},
                    {"gambling", getString(R.string.gambling)},
                    {"self_harm", getString(R.string.self_harm)},
                    {"sugar", getString(R.string.sugar)},
                    {"pornography", getString(R.string.pornography)},
                    {"social_networks", getString(R.string.social_networks)}
            };

            TextView predefinedLabel = new TextView(this);
            predefinedLabel.setText(getString(R.string.predefined_label));
            predefinedLabel.setPadding(0, 16, 0, 8);
            layout.addView(predefinedLabel);

            Spinner predefinedSpinner = new Spinner(this);
            List<String> predefinedItems = new ArrayList<>();
            predefinedItems.add(getString(R.string.predefined_placeholder));
            Map<String, String> keyMap = new HashMap<>();

            typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                predefinedItems.clear();
                predefinedItems.add(getString(R.string.predefined_placeholder));
                keyMap.clear();

                boolean isHabit = checkedId == habitRadio.getId();
                String[][] items = isHabit ? habitsWithKeys : addictionsWithKeys;
                Set<String> existingKeys = isHabit ? habitKeys : addictionKeys;

                for (String[] item : items) {
                    if (!existingKeys.contains(item[0])) {
                        predefinedItems.add(item[1]);
                        keyMap.put(item[1], item[0]);
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, predefinedItems);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                predefinedSpinner.setAdapter(adapter);
            });

            for (String[] habit : habitsWithKeys) {
                if (!habitKeys.contains(habit[0])) {
                    predefinedItems.add(habit[1]);
                    keyMap.put(habit[1], habit[0]);
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, predefinedItems);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            predefinedSpinner.setAdapter(adapter);
            layout.addView(predefinedSpinner);

            TextView nameLabel = new TextView(this);
            nameLabel.setText(getString(R.string.custom_name_label));
            nameLabel.setPadding(0, 16, 0, 8);
            layout.addView(nameLabel);

            EditText nameInput = new EditText(this);
            nameInput.setHint(getString(R.string.name_hint));
            layout.addView(nameInput);

            builder.setView(layout);
            builder.setPositiveButton(getString(R.string.add_button), (dialog, which) -> {
                boolean isHabit = typeGroup.getCheckedRadioButtonId() == habitRadio.getId();
                String selectedPredefined = predefinedSpinner.getSelectedItem().toString();
                String name = nameInput.getText().toString().trim();
                String key;

                if (!selectedPredefined.equals(getString(R.string.predefined_placeholder))) {
                    name = selectedPredefined;
                    key = keyMap.get(name);
                } else {
                    if (name.isEmpty()) {
                        Toast.makeText(this, getString(R.string.select_item_error), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    key = (isHabit ? "custom_habit_" : "custom_addiction_") + UUID.randomUUID().toString().substring(0, 8);
                }

                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(key, name);
                if (isHabit) {
                    habitKeys.add(key);
                    editor.putStringSet("selectedHabits", habitKeys);
                } else {
                    addictionKeys.add(key);
                    editor.putStringSet("selectedAddictions", addictionKeys);
                }
                editor.apply();

                HabitStats stats = new HabitStats();
                stats.startDate = new Date();
                stats.lastCheckDate = new Date();
                stats.nextReminder = getTomorrow();
                stats.reminderHour = isHabit ? 18 : addictionReminderHour;
                stats.reminderMinute = isHabit ? 0 : addictionReminderMinute;
                (isHabit ? habitStats : addictionStats).put(key, stats);

                saveStatsToPreferences(key, stats);
                if (currentUser != null) {
                    saveUserDataToFirebase(currentUser.getUid());
                    saveStatsToFirebase(currentUser.getUid(), key, stats, isHabit);
                }

                displayData();
                if (isHabit) {
                    showHabitsScreen();
                    rescheduleAllNotifications();
                } else {
                    showAddictionsScreen();
                }
                Toast.makeText(this, getString(R.string.item_added_toast), Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton(getString(R.string.cancel_button), null);
            builder.show();
        }
    }

    private boolean canAccessChat(DataSnapshot chatSnapshot, String userGender, Integer userAge) {
        String genderRestriction = chatSnapshot.child("gender_restriction").getValue(String.class);
        Long minAgeLong = chatSnapshot.child("min_age").getValue(Long.class);
        Long maxAgeLong = chatSnapshot.child("max_age").getValue(Long.class);
        Integer minAge = minAgeLong != null ? minAgeLong.intValue() : null;
        Integer maxAge = maxAgeLong != null ? maxAgeLong.intValue() : null;

        // Check gender restriction
        if (genderRestriction != null && !genderRestriction.equals("any") && userGender != null) {
            if (!userGender.toLowerCase(Locale.getDefault()).equals(genderRestriction.toLowerCase(Locale.getDefault()))) {
                return false;
            }
        }

        // Check age restriction
        if (userAge != null) {
            if (minAge != null && userAge < minAge) {
                return false;
            }
            return maxAge == null || userAge <= maxAge;
        }

        return true;
    }

    private void addChatCard(DataSnapshot chatSnapshot, boolean isGlobal, LinearLayout container) {
        String chatId = chatSnapshot.getKey();
        String chatName = chatSnapshot.child("name").getValue(String.class);
        String createdBy = chatSnapshot.child("created_by").getValue(String.class);

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

        TextView title = new TextView(this);
        String displayName = chatName != null ? chatName : getString(R.string.unnamed_chat);
        if (isGlobal) {
            displayName += " " + getString(R.string.global_chat_label);
        }
        title.setText(displayName);
        title.setTextSize(18);
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        title.setTextColor(ContextCompat.getColor(this, isDarkTheme ? R.color.teal_200 : R.color.purple_500));
        layout.addView(title);

        // Optionally display creator's username
        if (createdBy != null) {
            TextView creatorText = new TextView(this);
            database.child("users").child(createdBy).child("username").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String creatorName = snapshot.getValue(String.class);
                    creatorText.setText(getString(R.string.created_by_label, creatorName != null ? creatorName : getString(R.string.unknown_user)));
                    creatorText.setTextSize(14);
                    creatorText.setPadding(0, 4, 0, 0);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    creatorText.setText(getString(R.string.created_by_label, getString(R.string.unknown_user)));
                }
            });
            layout.addView(creatorText);
        }

        Button openButton = new Button(this);
        openButton.setText(R.string.open_chat_button);
        openButton.setOnClickListener(v -> {
            Intent intent = new Intent(ResultsActivity.this, CommunityActivity.class);
            intent.putExtra("CHAT_ID", chatId);
            intent.putExtra("CHAT_NAME", chatName);
            intent.putExtra("IS_GLOBAL", isGlobal);
            startActivity(intent);
        });
        layout.addView(openButton);

        container.addView(card);
    }

    private void fetchCustomHabitLabel(String habitKey, Callback<String> callback) {
        DatabaseReference usersRef = database.child("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String label = null;
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    DataSnapshot habitLabels = userSnapshot.child("habit_labels");
                    DataSnapshot addictionLabels = userSnapshot.child("addiction_labels");

                    if (habitLabels.child(habitKey).exists()) {
                        label = habitLabels.child(habitKey).getValue(String.class);
                        break;
                    } else if (addictionLabels.child(habitKey).exists()) {
                        label = addictionLabels.child(habitKey).getValue(String.class);
                        break;
                    }
                }
                callback.onResult(label);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching custom habit label: " + error.getMessage());
                callback.onResult(null);
            }
        });
    }

    // Callback interface for async operations
    private interface Callback<T> {
        void onResult(T result);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("OPEN_TAB")) {
            String tab = intent.getStringExtra("OPEN_TAB");
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if ("habits".equals(tab)) {
                bottomNav.setSelectedItemId(R.id.navigation_habits);
                showHabitsScreen();
            } else if ("addictions".equals(tab)) {
                bottomNav.setSelectedItemId(R.id.navigation_addictions);
                showAddictionsScreen();
            }
        }
    }

    private int getTargetDaysForProgress(String key) {
        switch (key) {
            // Habits
            case "healthy_sleep":
                return 30;
            case "morning_exercises":
            case "reading":
                return 66;
            case "meditation":
            case "social_networks":
            case "sugar":
                return 60;
            // Addictions
            case "alcohol":
            case "cigarettes":
            case "gambling":
            case "pornography":
                return 90;
            case "drugs":
            case "self_harm":
                return 120;
            default:
                // Custom habits/addictions
                if (key.startsWith("custom_habit_")) {
                    return 66;
                } else if (key.startsWith("custom_addiction_")) {
                    return 90;
                }
                return 66; // Fallback default
        }
    }

    private void markToday(String key, HabitStats stats) {
        if (hasRelapseToday(stats)) {
            Toast.makeText(this, getString(R.string.relapse_today_error), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isTodayMarked(stats)) {
            stats.markedDays.add(new Date());
            // Update progress for addictions when marking a victory
            updateStats(stats, false, key);

            saveStatsToPreferences(key, stats);
            if (currentUser != null) {
                saveStatsToFirebase(currentUser.getUid(), key, stats, false);
            }

            Toast.makeText(this, getString(R.string.day_marked_toast, getTextForKey(key)), Toast.LENGTH_SHORT).show();
            displayData();
        } else {
            Toast.makeText(this, getString(R.string.already_marked_toast), Toast.LENGTH_SHORT).show();
        }
    }

    private Date getYesterday() {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        return yesterday.getTime();
    }

    private void scheduleDailyCompletionCheck() {
        dailyCheckRunnable = () -> {
            Log.d(TAG, "scheduleDailyCompletionCheck: Running daily check at " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            checkDailyCompletions();
            scheduleNextDailyCheck();
        };
        scheduleNextDailyCheck();
    }

    private void scheduleNextDailyCheck() {
        dailyCheckHandler.removeCallbacks(dailyCheckRunnable);
        Calendar nextMidnight = Calendar.getInstance();
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);
        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
        long delay = nextMidnight.getTimeInMillis() - System.currentTimeMillis();
        Log.d(TAG, "scheduleNextDailyCheck: Next check scheduled in " + (delay / 1000) + " seconds");
        dailyCheckHandler.postDelayed(dailyCheckRunnable, delay);
    }

    private int[] calculateHabitStreaks(HabitStats stats) {
        int currentStreak = 0;
        int longestStreak = 0;

        if (stats.completionTimestamps.isEmpty()) {
            Log.d(TAG, "calculateHabitStreaks: No completions, streaks=0");
            return new int[]{0, 0};
        }

        List<Date> sortedTimestamps = new ArrayList<>(stats.completionTimestamps);
        Collections.sort(sortedTimestamps);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        List<String> completionDays = new ArrayList<>();
        for (Date timestamp : sortedTimestamps) {
            String day = fmt.format(timestamp);
            if (!completionDays.contains(day)) {
                completionDays.add(day);
            }
        }

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        String todayStr = fmt.format(today.getTime());

        if (completionDays.contains(todayStr)) {
            currentStreak = 1;
            for (int i = completionDays.size() - 2; i >= 0; i--) {
                Calendar currentDay = Calendar.getInstance();
                try {
                    currentDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i))));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                Calendar nextDay = Calendar.getInstance();
                try {
                    nextDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i + 1))));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }

                long daysDiff = TimeUnit.MILLISECONDS.toDays(nextDay.getTimeInMillis() - currentDay.getTimeInMillis());
                if (daysDiff == 1) {
                    currentStreak++;
                } else {
                    break;
                }
            }
        } else if (!completionDays.isEmpty()) {
            Calendar lastCompletionDay = Calendar.getInstance();
            try {
                lastCompletionDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(completionDays.size() - 1))));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            long daysSinceLast = TimeUnit.MILLISECONDS.toDays(today.getTimeInMillis() - lastCompletionDay.getTimeInMillis());
            if (daysSinceLast <= 1) {
                currentStreak = 1;
                for (int i = completionDays.size() - 2; i >= 0; i--) {
                    Calendar currentDay = Calendar.getInstance();
                    try {
                        currentDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i))));
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                    Calendar nextDay = Calendar.getInstance();
                    try {
                        nextDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i + 1))));
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }

                    long daysDiff = TimeUnit.MILLISECONDS.toDays(nextDay.getTimeInMillis() - currentDay.getTimeInMillis());
                    if (daysDiff == 1) {
                        currentStreak++;
                    } else {
                        break;
                    }
                }
            }
        }

        int tempStreak = 1;
        for (int i = 0; i < completionDays.size() - 1; i++) {
            Calendar currentDay = Calendar.getInstance();
            try {
                currentDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i))));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            Calendar nextDay = Calendar.getInstance();
            try {
                nextDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i + 1))));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

            long daysDiff = TimeUnit.MILLISECONDS.toDays(nextDay.getTimeInMillis() - currentDay.getTimeInMillis());
            if (daysDiff == 1) {
                tempStreak++;
            } else {
                longestStreak = Math.max(longestStreak, tempStreak);
                tempStreak = 1;
            }
        }
        longestStreak = Math.max(longestStreak, tempStreak);

        Log.d(TAG, "calculateHabitStreaks: currentStreak=" + currentStreak + ", longestStreak=" + longestStreak);
        return new int[]{currentStreak, longestStreak};
    }

    private void saveStatsToFirebase(String userId, String key, HabitStats stats, boolean isHabit) {
        if (stats == null) return;
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
        Log.d(TAG, "saveStatsToFirebase: Key=" + key + ", isHabit=" + isHabit + ", completionTimestamps=" + completionTimestampsStrings.size());
    }

    private void updateStats(HabitStats stats, boolean isHabit, String key) {
        if (isHabit) {
            int targetDays = getTargetDaysForProgress(key);
            int currentStreak = calculateHabitStreaks(stats)[0];
            stats.progress = Math.min((int) ((currentStreak / (float) targetDays) * 100), 100);
            Log.d(TAG, "updateStats: Key=" + key + ", isHabit=true, currentStreak=" + currentStreak + ", progress=" + stats.progress);
        } else {
            long streakMillis = stats.relapseDates.isEmpty() ?
                    System.currentTimeMillis() - stats.startDate.getTime() :
                    System.currentTimeMillis() - Collections.max(stats.relapseDates).getTime();
            int currentStreak = (int) TimeUnit.MILLISECONDS.toDays(streakMillis);
            int targetDays = getTargetDaysForProgress(key);
            stats.progress = Math.min((int) ((currentStreak / (float) targetDays) * 100), 100);
            Log.d(TAG, "updateStats: Key=" + key + ", isHabit=false, currentStreak=" + currentStreak + ", progress=" + stats.progress);
        }
    }

    private void markHabitCompletion(String key, HabitStats stats) {
        Date now = new Date();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayStr = fmt.format(now);

        stats.completionTimestamps.add(now);
        Log.d(TAG, "markHabitCompletion: Key=" + key + ", added timestamp=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now));

        boolean isFirstCompletionToday = stats.completionTimestamps.stream()
                .filter(timestamp -> fmt.format(timestamp).equals(todayStr))
                .count() == 1;

        if (isFirstCompletionToday) {
            Calendar lastCheck = Calendar.getInstance();
            lastCheck.setTime(stats.lastCheckDate);
            lastCheck.set(Calendar.HOUR_OF_DAY, 0);
            lastCheck.set(Calendar.MINUTE, 0);
            lastCheck.set(Calendar.SECOND, 0);
            lastCheck.set(Calendar.MILLISECOND, 0);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            long daysDiff = TimeUnit.MILLISECONDS.toDays(today.getTimeInMillis() - lastCheck.getTimeInMillis());
            Log.d(TAG, "markHabitCompletion: Key=" + key + ", daysDiff=" + daysDiff);

            stats.lastCheckDate = now;
            updateStats(stats, true, key);
        } else {
            Log.d(TAG, "markHabitCompletion: Key=" + key + ", additional completion today");
        }

        saveStatsToPreferences(key, stats);
        if (currentUser != null) {
            saveStatsToFirebase(currentUser.getUid(), key, stats, true);
        }
        Toast.makeText(this, getString(R.string.habit_completed_toast, getTextForKey(key)), Toast.LENGTH_SHORT).show();
        displayData();
    }

    private void checkDailyCompletions() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String yesterdayStr = fmt.format(getYesterday().getTime());
        String todayStr = fmt.format(new Date());

        for (String key : habitKeys) {
            HabitStats stats = habitStats.get(key);
            if (stats == null) {
                Log.w(TAG, "checkDailyCompletions: No stats for key=" + key);
                continue;
            }

            boolean completedYesterday = stats.completionTimestamps.stream()
                    .anyMatch(timestamp -> fmt.format(timestamp).equals(yesterdayStr));
            boolean completedToday = stats.completionTimestamps.stream()
                    .anyMatch(timestamp -> fmt.format(timestamp).equals(todayStr));

            Log.d(TAG, "checkDailyCompletions: Key=" + key + ", completedYesterday=" + completedYesterday + ", completedToday=" + completedToday);

            if (!completedYesterday && !completedToday) {
                Calendar lastCheck = Calendar.getInstance();
                lastCheck.setTime(stats.lastCheckDate);
                lastCheck.set(Calendar.HOUR_OF_DAY, 0);
                lastCheck.set(Calendar.MINUTE, 0);
                lastCheck.set(Calendar.SECOND, 0);
                lastCheck.set(Calendar.MILLISECOND, 0);

                Calendar yesterday = Calendar.getInstance();
                yesterday.set(Calendar.HOUR_OF_DAY, 0);
                yesterday.set(Calendar.MINUTE, 0);
                yesterday.set(Calendar.SECOND, 0);
                yesterday.set(Calendar.MILLISECOND, 0);
                yesterday.add(Calendar.DAY_OF_YEAR, -1);

                long daysDiff = TimeUnit.MILLISECONDS.toDays(yesterday.getTimeInMillis() - lastCheck.getTimeInMillis());
                if (daysDiff >= 1) {
                    stats.progress = Math.max(0, stats.progress - 5);
                    Log.d(TAG, "checkDailyCompletions: Key=" + key + ", progress=" + stats.progress);
                }
            }

            saveStatsToPreferences(key, stats);
            if (currentUser != null) {
                saveStatsToFirebase(currentUser.getUid(), key, stats, true);
            }
        }
        displayData();
    }

    private void showTimePickerDialog(String key, HabitStats stats, Calendar dateCalendar) {
        new TimePickerDialog(this,
                (view, hour, minute) -> {
                    dateCalendar.set(Calendar.HOUR_OF_DAY, hour);
                    dateCalendar.set(Calendar.MINUTE, minute);
                    dateCalendar.set(Calendar.SECOND, 0);
                    dateCalendar.set(Calendar.MILLISECOND, 0);

                    if (dateCalendar.getTimeInMillis() > System.currentTimeMillis()) {
                        Toast.makeText(this, getString(R.string.future_time_error), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Date relapseDate = dateCalendar.getTime();
                    boolean isLatest = stats.relapseDates.isEmpty() || stats.relapseDates.stream().noneMatch(d -> d.after(relapseDate));
                    stats.relapseDates.add(relapseDate);

                    SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                    String relapseDay = fmt.format(relapseDate);
                    stats.markedDays.removeIf(day -> fmt.format(day).equals(relapseDay));

                    if (isLatest) {
                        stats.progress = Math.max(0, stats.progress - 5);
                        stats.lastCheckDate = relapseDate;
                        Toast.makeText(this, getString(R.string.relapse_recorded_toast), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.relapse_no_progress_change_toast), Toast.LENGTH_SHORT).show();
                    }

                    Calendar nextReminder = Calendar.getInstance();
                    nextReminder.set(Calendar.HOUR_OF_DAY, addictionReminderHour);
                    nextReminder.set(Calendar.MINUTE, addictionReminderMinute);
                    nextReminder.set(Calendar.SECOND, 0);
                    if (nextReminder.getTimeInMillis() <= System.currentTimeMillis()) {
                        nextReminder.add(Calendar.DAY_OF_YEAR, 1);
                    }
                    stats.nextReminder = nextReminder.getTime();

                    saveStatsToPreferences(key, stats);
                    if (currentUser != null) {
                        saveStatsToFirebase(currentUser.getUid(), key, stats, false);
                    }
                    displayData();
                },
                dateCalendar.get(Calendar.HOUR_OF_DAY), dateCalendar.get(Calendar.MINUTE), true).show();
    }

    private void checkAndUpdateGoalStatus(String goalId, Goal goal) {
        HabitStats stats = habitStats.containsKey(goal.habitKey) ?
                habitStats.get(goal.habitKey) :
                addictionStats.get(goal.habitKey);
        if (stats == null) return;

        boolean isAchieved = false;
        boolean isHabit = habitStats.containsKey(goal.habitKey);

        if ("streak".equals(goal.type)) {
            int currentStreak = isHabit ? calculateHabitStreaks(stats)[0] :
                    (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() -
                            (stats.relapseDates.isEmpty() ? stats.startDate.getTime() :
                                    Collections.max(stats.relapseDates).getTime()));
            isAchieved = currentStreak >= goal.target;
            Log.d(TAG, "checkAndUpdateGoalStatus: GoalId=" + goalId + ", habitKey=" + goal.habitKey +
                    ", isHabit=" + isHabit + ", currentStreak=" + currentStreak + ", target=" + goal.target);
        } else if ("progress".equals(goal.type)) {
            isAchieved = stats.progress >= goal.target;
            Log.d(TAG, "checkAndUpdateGoalStatus: GoalId=" + goalId + ", habitKey=" + goal.habitKey +
                    ", progress=" + stats.progress + ", target=" + goal.target);
        }

        if (isAchieved && !goal.achieved) {
            goal.achieved = true;
            saveGoalToPreferences(goalId, goal);
            if (currentUser != null) {
                saveGoalToFirebase(currentUser.getUid(), goalId, goal);
            }
            Toast.makeText(this, getString(R.string.goal_achieved_toast, getTextForKey(goal.habitKey)), Toast.LENGTH_LONG).show();
        }
    }

    private ResultsActivity.HabitStats parseStatsFromFirebase(DataSnapshot snapshot) {
        ResultsActivity.HabitStats stats = new ResultsActivity.HabitStats();
        try {
            String startDateStr = snapshot.child("start_date").getValue(String.class);
            stats.startDate = startDateStr != null ? dateFormat.parse(startDateStr) : new Date();

            Long progress = snapshot.child("progress").getValue(Long.class);
            stats.progress = progress != null ? progress.intValue() : 0;

            String nextReminderStr = snapshot.child("next_reminder").getValue(String.class);
            stats.nextReminder = nextReminderStr != null ? dateFormat.parse(nextReminderStr) : HabitUtils.getDefaultReminderTime();

            String lastCheckStr = snapshot.child("last_check_date").getValue(String.class);
            stats.lastCheckDate = lastCheckStr != null ? dateFormat.parse(lastCheckStr) : stats.startDate;

            Long reminderInterval = snapshot.child("reminder_interval").getValue(Long.class);
            stats.reminderInterval = reminderInterval != null ? reminderInterval.intValue() : 1;

            Long reminderHour = snapshot.child("reminder_hour").getValue(Long.class);
            stats.reminderHour = reminderHour != null ? reminderHour.intValue() : 18;

            Long reminderMinute = snapshot.child("reminder_minute").getValue(Long.class);
            stats.reminderMinute = reminderMinute != null ? reminderMinute.intValue() : 0;

            stats.markedDays = new ArrayList<>();
            if (snapshot.child("marked_days").exists()) {
                for (DataSnapshot daySnapshot : snapshot.child("marked_days").getChildren()) {
                    String dateStr = daySnapshot.getValue(String.class);
                    if (dateStr != null) {
                        Date date = dateOnlyFormat.parse(dateStr);
                        if (date != null) stats.markedDays.add(date);
                    }
                }
            }

            stats.relapseDates = new ArrayList<>();
            if (snapshot.child("relapse_dates").exists()) {
                for (DataSnapshot relapseSnapshot : snapshot.child("relapse_dates").getChildren()) {
                    String dateStr = relapseSnapshot.getValue(String.class);
                    if (dateStr != null) {
                        Date date = dateFormat.parse(dateStr);
                        if (date != null) stats.relapseDates.add(date);
                    }
                }
            }

            stats.completionTimestamps = new ArrayList<>();
            if (snapshot.child("completion_timestamps").exists()) {
                for (DataSnapshot timestampSnapshot : snapshot.child("completion_timestamps").getChildren()) {
                    String timestampStr = timestampSnapshot.getValue(String.class);
                    if (timestampStr != null) {
                        Date timestamp = dateFormat.parse(timestampStr);
                        if (timestamp != null) stats.completionTimestamps.add(timestamp);
                    }
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing stats: " + e.getMessage());
            stats.nextReminder = HabitUtils.getDefaultReminderTime();
            stats.reminderInterval = 1;
            stats.reminderHour = 18;
            stats.reminderMinute = 0;
        }
        return stats;
    }

    private HabitStats getStatsFromPreferences(String key) {
        HabitStats stats = new HabitStats();
        try {
            String startDateStr = statsPrefs.getString(key + "_start_date", null);
            stats.startDate = startDateStr != null ? dateFormat.parse(startDateStr) : new Date();

            stats.progress = statsPrefs.getInt(key + "_progress", 0);

            String nextReminderStr = statsPrefs.getString(key + "_next_reminder", null);
            stats.nextReminder = nextReminderStr != null ? dateFormat.parse(nextReminderStr) : HabitUtils.getTomorrow();

            String lastCheckStr = statsPrefs.getString(key + "_last_check_date", null);
            stats.lastCheckDate = lastCheckStr != null ? dateFormat.parse(lastCheckStr) : stats.startDate;

            stats.reminderInterval = statsPrefs.getInt(key + "_reminder_interval", 1);
            stats.reminderHour = statsPrefs.getInt(key + "_reminder_hour", 18);
            stats.reminderMinute = statsPrefs.getInt(key + "_reminder_minute", 0);

            stats.markedDays = new ArrayList<>();
            Set<String> markedDaysSet = statsPrefs.getStringSet(key + "_marked_days", new java.util.HashSet<>());
            for (String dateStr : markedDaysSet) {
                Date date = dateOnlyFormat.parse(dateStr);
                if (date != null) stats.markedDays.add(date);
            }

            stats.relapseDates = new ArrayList<>();
            Set<String> relapseDatesSet = statsPrefs.getStringSet(key + "_relapse_dates", new java.util.HashSet<>());
            for (String dateStr : relapseDatesSet) {
                Date date = dateFormat.parse(dateStr);
                if (date != null) stats.relapseDates.add(date);
            }

            stats.completionTimestamps = new ArrayList<>();
            Set<String> completionTimestampsSet = statsPrefs.getStringSet(key + "_completion_timestamps", new java.util.HashSet<>());
            for (String timestampStr : completionTimestampsSet) {
                Date timestamp = dateFormat.parse(timestampStr);
                if (timestamp != null) stats.completionTimestamps.add(timestamp);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing stats from preferences: " + e.getMessage());
        }
        return stats;
    }

    public static class HabitStats {
        public Date startDate = new Date();
        public int progress;
        public Date nextReminder;
        public Date lastCheckDate = startDate;
        public int reminderHour = 18;
        public int reminderMinute = 0;
        public int reminderInterval = 1;
        public List<Date> relapseDates = new ArrayList<>();
        public List<Date> markedDays = new ArrayList<>();
        public List<Date> completionTimestamps = new ArrayList<>();

        public long calculateLongestStreakMillis() {
            if (relapseDates.isEmpty()) {
                return System.currentTimeMillis() - startDate.getTime();
            }

            List<Date> sortedRelapses = new ArrayList<>(relapseDates);
            Collections.sort(sortedRelapses);
            long longestStreakMillis = 0;

            for (int i = 0; i < sortedRelapses.size() - 1; i++) {
                long diff = sortedRelapses.get(i + 1).getTime() - sortedRelapses.get(i).getTime();
                longestStreakMillis = Math.max(longestStreakMillis, diff);
            }

            longestStreakMillis = Math.max(longestStreakMillis, sortedRelapses.get(0).getTime() - startDate.getTime());
            longestStreakMillis = Math.max(longestStreakMillis, System.currentTimeMillis() - sortedRelapses.get(sortedRelapses.size() - 1).getTime());

            return longestStreakMillis;
        }

    }

    private void setupStreakUpdate(String key, TextView streakText, TextView longestStreakText, ProgressBar progressBar,
                                   TextView progressText, HabitStats stats, boolean isHabit) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                updateStats(stats, isHabit, key);
                if (isHabit) {
                    int[] streaks = HabitUtils.calculateHabitStreaks(stats);
                    streakText.setText(getString(R.string.current_streak_label) + " " + streaks[0] + " " + getString(R.string.days_label));
                    longestStreakText.setText(getString(R.string.longest_streak_label) + " " + streaks[1] + " " + getString(R.string.days_label));
                } else {
                    long currentStreakMillis = stats.relapseDates.isEmpty() ?
                            System.currentTimeMillis() - stats.startDate.getTime() :
                            System.currentTimeMillis() - Collections.max(stats.relapseDates).getTime();
                    long longestStreakMillis = stats.calculateLongestStreakMillis();
                    streakText.setText(getString(R.string.current_streak_label) + " " + HabitUtils.formatDuration(currentStreakMillis, ResultsActivity.this));
                    longestStreakText.setText(getString(R.string.longest_streak_label) + " " + HabitUtils.formatDuration(longestStreakMillis, ResultsActivity.this));
                }
                progressBar.setProgress(stats.progress);
                progressText.setText(getString(R.string.progress_label) + " " + stats.progress + "%");
                Log.d(TAG, "setupStreakUpdate: Updated key=" + key + ", isHabit=" + isHabit);
                streakUpdateHandler.postDelayed(this, 60000);
            }
        };
        streakUpdateRunnables.put(key, runnable);
        streakUpdateHandler.post(runnable);
    }

    private int checkMissedDay(HabitStats stats) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayStr = fmt.format(new Date());

        boolean completedToday = stats.completionTimestamps.stream()
                .anyMatch(timestamp -> fmt.format(timestamp).equals(todayStr));
        if (completedToday) {
            Log.d(TAG, "checkMissedDay: Habit completed today, no missed days");
            return 0;
        }

        Calendar startDate = Calendar.getInstance();
        startDate.setTime(stats.startDate);
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        long daysSinceStart = TimeUnit.MILLISECONDS.toDays(today.getTimeInMillis() - startDate.getTimeInMillis());
        if (daysSinceStart < 1) {
            Log.d(TAG, "checkMissedDay: Less than one day since start, no missed days");
            return 0;
        }

        if (stats.completionTimestamps.isEmpty()) {
            Log.d(TAG, "checkMissedDay: No completions, missed days=" + daysSinceStart);
            return (int) daysSinceStart;
        }

        Date lastCompletion = Collections.max(stats.completionTimestamps);
        Calendar lastCompletionCal = Calendar.getInstance();
        lastCompletionCal.setTime(lastCompletion);
        lastCompletionCal.set(Calendar.HOUR_OF_DAY, 0);
        lastCompletionCal.set(Calendar.MINUTE, 0);
        lastCompletionCal.set(Calendar.SECOND, 0);
        lastCompletionCal.set(Calendar.MILLISECOND, 0);

        long missedDays = TimeUnit.MILLISECONDS.toDays(today.getTimeInMillis() - lastCompletionCal.getTimeInMillis()) - 1;
        missedDays = Math.max(0, missedDays);
        Log.d(TAG, "checkMissedDay: Last completion=" + fmt.format(lastCompletion) + ", missed days=" + missedDays);
        return (int) missedDays;
    }

    private void createHabitCards(Set<String> keys, Map<String, HabitStats> statsMap, LinearLayout container, boolean isHabit) {
        container.removeAllViews();
        if (keys.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText(isHabit ? R.string.no_habits_selected : R.string.no_addictions_selected);
            emptyText.setPadding(32, 32, 32, 32);
            emptyText.setTextSize(16);
            container.addView(emptyText);
            return;
        }

        for (String key : keys) {
            HabitStats stats = statsMap.computeIfAbsent(key, k -> {
                HabitStats newStats = new HabitStats();
                newStats.startDate = new Date();
                newStats.lastCheckDate = new Date();
                newStats.nextReminder = HabitUtils.getTomorrow();
                newStats.reminderHour = isHabit ? 18 : addictionReminderHour;
                newStats.reminderMinute = isHabit ? 0 : addictionReminderMinute;
                return newStats;
            });

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

            TextView title = new TextView(this);
            title.setText(getTextForKey(key));
            title.setTextSize(18);
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
            title.setTextColor(ContextCompat.getColor(this, isDarkTheme ? R.color.teal_200 : R.color.purple_500));
            layout.addView(title);

            if (isHabit) {
                int missedDays = checkMissedDay(stats);
                if (missedDays > 0) {
                    TextView missedDayText = new TextView(this);
                    missedDayText.setText(getString(R.string.missed_days_warning, missedDays));
                    missedDayText.setTextColor(Color.RED);
                    missedDayText.setPadding(0, 4, 0, 4);
                    layout.addView(missedDayText);
                }
            }

            TextView startDate = new TextView(this);
            startDate.setText(getString(R.string.start_date_label) + " " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(stats.startDate));
            startDate.setPadding(0, 8, 0, 0);
            layout.addView(startDate);

            TextView streakText = new TextView(this);
            TextView longestStreakText = new TextView(this);

            if (isHabit) {
                int[] streaks = HabitUtils.calculateHabitStreaks(stats);
                streakText.setText(getString(R.string.current_streak_label) + " " + streaks[0] + " " + getString(R.string.days_label));
                longestStreakText.setText(getString(R.string.longest_streak_label) + " " + streaks[1] + " " + getString(R.string.days_label));
            } else {
                long currentStreakMillis = stats.relapseDates.isEmpty() ?
                        System.currentTimeMillis() - stats.startDate.getTime() :
                        System.currentTimeMillis() - Collections.max(stats.relapseDates).getTime();
                long longestStreakMillis = stats.calculateLongestStreakMillis();
                streakText.setText(getString(R.string.current_streak_label) + " " + HabitUtils.formatDuration(currentStreakMillis, this));
                longestStreakText.setText(getString(R.string.longest_streak_label) + " " + HabitUtils.formatDuration(longestStreakMillis, this));
            }
            streakText.setPadding(0, 4, 0, 0);
            longestStreakText.setPadding(0, 4, 0, 0);
            layout.addView(streakText);
            layout.addView(longestStreakText);

            ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            progressBar.setMax(100);
            progressBar.setProgress(stats.progress);
            progressBar.setPadding(0, 8, 0, 0);
            layout.addView(progressBar);

            TextView progressText = new TextView(this);
            progressText.setText(getString(R.string.progress_label) + " " + stats.progress + "%");
            progressText.setPadding(0, 4, 0, 8);
            layout.addView(progressText);

            if (isHabit) {
                Button markCompletionButton = new Button(this);
                markCompletionButton.setText(R.string.mark_completion_button);
                markCompletionButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                markCompletionButton.setOnClickListener(v -> markHabitCompletion(key, stats));
                layout.addView(markCompletionButton);

                card.setOnClickListener(v -> {
                    Intent intent = new Intent(ResultsActivity.this, HabitStatsActivity.class);
                    intent.putExtra("HABIT_KEY", key);
                    intent.putExtra("IS_HABIT", true);
                    startActivity(intent);
                });
            }

            updateStats(stats, isHabit, key);
            setupStreakUpdate(key, streakText, longestStreakText, progressBar, progressText, stats, isHabit);
            saveStatsToPreferences(key, stats);
            if (currentUser != null) {
                saveStatsToFirebase(currentUser.getUid(), key, stats, isHabit);
            }

            if (!isHabit) {
                LinearLayout button2Container = new LinearLayout(this);
                button2Container.setOrientation(LinearLayout.HORIZONTAL);
                button2Container.setGravity(Gravity.CENTER_VERTICAL);
                button2Container.setPadding(0, 8, 0, 0);
                layout.addView(button2Container);

                Button markButton = new Button(this);
                markButton.setText(getString(R.string.mark_victory_button));
                markButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                markButton.setOnClickListener(v -> markToday(key, stats));
                button2Container.addView(markButton);

                Button panicButton = new Button(this);
                panicButton.setText(getString(R.string.panic_button));
                panicButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                panicButton.setOnClickListener(v -> showPanicAnimation());
                button2Container.addView(panicButton);

                card.setOnClickListener(v -> {
                    Intent intent = new Intent(ResultsActivity.this, AddictionStatsActivity.class);
                    intent.putExtra("ADDICTION_KEY", key);
                    intent.putExtra("IS_HABIT", false);
                    startActivity(intent);
                });
            }

            LinearLayout buttonContainer = new LinearLayout(this);
            buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
            buttonContainer.setGravity(Gravity.CENTER_VERTICAL);
            buttonContainer.setPadding(0, 8, 0, 0);
            layout.addView(buttonContainer);

            Button actionButton = new Button(this);
            actionButton.setText(isHabit ? getString(R.string.reminder_button) : getString(R.string.relapse_button));
            actionButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            actionButton.setOnClickListener(v -> {
                if (isHabit) {
                    showReminderDialog(key, stats);
                } else {
                    showRelapseDialog(key, stats);
                }
            });
            buttonContainer.addView(actionButton);

            Button deleteButton = new Button(this);
            deleteButton.setText(getString(R.string.delete_button));
            deleteButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            deleteButton.setOnClickListener(v -> showDeleteConfirmationDialog(key, isHabit));
            buttonContainer.addView(deleteButton);

            container.addView(card);
        }
    }

    private static final String PENDING_CHANGES_PREFS = "PendingChangesPrefs";
    private SharedPreferences pendingChangesPrefs;
    private static final String PENDING_CHANGES_KEY = "pending_changes";

    private void loadData() {
        String lastUserId = sharedPrefs.getString("last_user_id", "");
        String currentUserId = currentUser != null ? currentUser.getUid() : "";
        if (!currentUserId.isEmpty() && !lastUserId.equals(currentUserId)) {
            clearLocalData();
            sharedPrefs.edit().putString("last_user_id", currentUserId).apply();
        }

        if (currentUser != null && HabitUtils.isNetworkAvailable(this)) {
            loadDataFromFirebase(currentUser.getUid());
        } else {
            loadDataFromLocalStorage();
            Toast.makeText(this, R.string.offline_mode_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettingsToFirebase() {
        if (currentUser == null) return;
        DatabaseReference settingsRef = database.child("users").child(currentUser.getUid()).child("settings");
        Map<String, Object> settingsMap = new HashMap<>();
        settingsMap.put("sleep_mode_enabled", sleepModeEnabled);
        settingsMap.put("sleep_start_hour", sleepStartHour);
        settingsMap.put("sleep_start_minute", sleepStartMinute);
        settingsMap.put("sleep_end_hour", sleepEndHour);
        settingsMap.put("sleep_end_minute", sleepEndMinute);
        settingsMap.put("addiction_reminders_enabled", addictionRemindersEnabled);
        settingsMap.put("addiction_reminder_hour", addictionReminderHour);
        settingsMap.put("addiction_reminder_minute", addictionReminderMinute);
        settingsRef.setValue(settingsMap);
    }

    private void loadSettings() {
        if (currentUser != null && HabitUtils.isNetworkAvailable(this)) {
            database.child("users").child(currentUser.getUid()).child("settings")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                sleepModeEnabled = snapshot.child("sleep_mode_enabled").getValue(Boolean.class) != null && Boolean.TRUE.equals(snapshot.child("sleep_mode_enabled").getValue(Boolean.class));
                                sleepStartHour = snapshot.child("sleep_start_hour").getValue(Long.class) != null
                                        ? Objects.requireNonNull(snapshot.child("sleep_start_hour").getValue(Long.class)).intValue() : 22;
                                sleepStartMinute = snapshot.child("sleep_start_minute").getValue(Long.class) != null
                                        ? Objects.requireNonNull(snapshot.child("sleep_start_minute").getValue(Long.class)).intValue() : 0;
                                sleepEndHour = snapshot.child("sleep_end_hour").getValue(Long.class) != null
                                        ? Objects.requireNonNull(snapshot.child("sleep_end_hour").getValue(Long.class)).intValue() : 7;
                                sleepEndMinute = snapshot.child("sleep_end_minute").getValue(Long.class) != null
                                        ? Objects.requireNonNull(snapshot.child("sleep_end_minute").getValue(Long.class)).intValue() : 0;
                                addictionRemindersEnabled = snapshot.child("addiction_reminders_enabled").getValue(Boolean.class) != null && Boolean.TRUE.equals(snapshot.child("addiction_reminders_enabled").getValue(Boolean.class));
                                addictionReminderHour = snapshot.child("addiction_reminder_hour").getValue(Long.class) != null
                                        ? Objects.requireNonNull(snapshot.child("addiction_reminder_hour").getValue(Long.class)).intValue() : 18;
                                addictionReminderMinute = snapshot.child("addiction_reminder_minute").getValue(Long.class) != null
                                        ? Objects.requireNonNull(snapshot.child("addiction_reminder_minute").getValue(Long.class)).intValue() : 0;

                                saveSettingsToPreferences();
                            } else {
                                loadSettingsFromPreferences();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loadSettingsFromPreferences();
                        }
                    });
        } else {
            loadSettingsFromPreferences();
        }
    }

    private void loadSettingsFromPreferences() {
        sleepModeEnabled = notifPrefs.getBoolean("sleep_mode_enabled", false);
        sleepStartHour = notifPrefs.getInt("sleep_start_hour", 22);
        sleepStartMinute = notifPrefs.getInt("sleep_start_minute", 0);
        sleepEndHour = notifPrefs.getInt("sleep_end_hour", 7);
        sleepEndMinute = notifPrefs.getInt("sleep_end_minute", 0);
        addictionRemindersEnabled = addictionPrefs.getBoolean("addiction_reminders_enabled", false);
        addictionReminderHour = addictionPrefs.getInt("addiction_reminder_hour", 18);
        addictionReminderMinute = addictionPrefs.getInt("addiction_reminder_minute", 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        initializeFirebaseAndPrefs();
        pendingChangesPrefs = getSharedPreferences(PENDING_CHANGES_PREFS, MODE_PRIVATE);
        loadSettings();
        initializeUI();
        setupNavigation();
        setupAddButton();
        checkPermissions();
        startNotificationService();
        loadData();
        handleIntent(getIntent());
        scheduleDailyCompletionCheck();
    }

    private void initializeFirebaseAndPrefs() {
        database = FirebaseDatabase.getInstance().getReference();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        sharedPrefs = getSharedPreferences("HabitsPrefs", MODE_PRIVATE);
        statsPrefs = getSharedPreferences("StatsPrefs", MODE_PRIVATE);
        notifPrefs = getSharedPreferences("NotificationSettings", MODE_PRIVATE);
        addictionPrefs = getSharedPreferences("AddictionReminderSettings", MODE_PRIVATE);
        goalsPrefs = getSharedPreferences("GoalsPrefs", MODE_PRIVATE);
    }

    private void queueChange(String changeType, String key) {
        Set<String> pendingChanges = new HashSet<>(pendingChangesPrefs.getStringSet(PENDING_CHANGES_KEY, new HashSet<>()));
        String change = changeType + (key != null ? ":" + key : "");
        pendingChanges.add(change);
        pendingChangesPrefs.edit().putStringSet(PENDING_CHANGES_KEY, pendingChanges).apply();
        Log.d(TAG, "Queued change: " + change);

        // Schedule background sync
        scheduleBackgroundSync();
    }

    private void scheduleBackgroundSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncWorkRequest = new OneTimeWorkRequest.Builder(FirebaseSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this)
                .enqueueUniqueWork("firebase_sync", ExistingWorkPolicy.KEEP, syncWorkRequest);

        Log.d(TAG, "Scheduled background sync with WorkManager");
    }

    private void syncPendingChanges() {
        if (currentUser == null || !HabitUtils.isNetworkAvailable(this)) {
            Log.w(TAG, "Cannot sync: no user or no network");
            return;
        }

        Set<String> pendingChanges = new HashSet<>(pendingChangesPrefs.getStringSet(PENDING_CHANGES_KEY, new HashSet<>()));
        if (pendingChanges.isEmpty()) {
            Log.d(TAG, "No pending changes to sync");
            return;
        }

        for (String change : pendingChanges) {
            String[] parts = change.split(":");
            String changeType = parts[0];
            String key = parts.length > 1 ? parts[1] : null;

            try {
                if ("keys_and_labels".equals(changeType)) {
                    saveUserDataToFirebase(currentUser.getUid());
                    for (Map.Entry<String, Goal> entry : goals.entrySet()) {
                        saveGoalToFirebase(currentUser.getUid(), entry.getKey(), entry.getValue());
                    }
                } else if (changeType.startsWith("stats_")) {
                    boolean isHabit = habitKeys.contains(key);
                    HabitStats stats = isHabit ? habitStats.get(key) : addictionStats.get(key);
                    if (stats != null) {
                        saveStatsToFirebase(currentUser.getUid(), key, stats, isHabit);
                    }
                } else if ("settings".equals(changeType)) {
                    saveSettingsToFirebase();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing change: " + change + ", " + e.getMessage());
            }
        }

        // Clear only successfully synced changes
        pendingChangesPrefs.edit().remove(PENDING_CHANGES_KEY).apply();
        Toast.makeText(this, R.string.data_synced, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Pending changes synced successfully");
    }

    private void saveStatsToPreferences(String key, HabitStats stats) {
        SharedPreferences.Editor editor = statsPrefs.edit();
        editor.putString(key + "_start_date", dateFormat.format(stats.startDate));
        editor.putInt(key + "_progress", stats.progress);
        editor.putString(key + "_next_reminder", dateFormat.format(stats.nextReminder));
        if (stats.lastCheckDate != null) {
            editor.putString(key + "_last_check_date", dateFormat.format(stats.lastCheckDate));
        }
        editor.putInt(key + "_reminder_interval", stats.reminderInterval);
        editor.putInt(key + "_reminder_hour", stats.reminderHour);
        editor.putInt(key + "_reminder_minute", stats.reminderMinute);

        Set<String> markedDaysSet = new HashSet<>();
        for (Date date : stats.markedDays) {
            markedDaysSet.add(dateOnlyFormat.format(date));
        }
        editor.putStringSet(key + "_marked_days", markedDaysSet);

        Set<String> relapseDatesSet = new HashSet<>();
        for (Date date : stats.relapseDates) {
            relapseDatesSet.add(dateFormat.format(date));
        }
        editor.putStringSet(key + "_relapse_dates", relapseDatesSet);

        Set<String> completionTimestampsSet = new HashSet<>();
        for (Date timestamp : stats.completionTimestamps) {
            completionTimestampsSet.add(dateFormat.format(timestamp));
        }
        editor.putStringSet(key + "_completion_timestamps", completionTimestampsSet);

        editor.apply();

        // Queue for Firebase sync if offline
        if (currentUser != null && !HabitUtils.isNetworkAvailable(this)) {
            queueChange("stats_" + key, key);
        } else if (currentUser != null) {
            syncPendingChanges();
        }
    }

    private void saveKeysToSharedPreferences(Set<String> habitKeys, Set<String> addictionKeys,
                                             Map<String, String> habitLabels, Map<String, String> addictionLabels,
                                             Map<String, Goal> goals) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putStringSet("selectedHabits", habitKeys);
        editor.putStringSet("selectedAddictions", addictionKeys);
        for (Map.Entry<String, String> entry : habitLabels.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : addictionLabels.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        if (currentUser != null) {
            editor.putString("last_user_id", currentUser.getUid());
        }
        editor.apply();

        // Save goals
        SharedPreferences.Editor goalsEditor = goalsPrefs.edit();
        for (Map.Entry<String, Goal> entry : goals.entrySet()) {
            String goalId = entry.getKey();
            Goal goal = entry.getValue();
            goalsEditor.putString(goalId + "_habitKey", goal.habitKey);
            goalsEditor.putString(goalId + "_type", goal.type);
            goalsEditor.putInt(goalId + "_target", goal.target);
            goalsEditor.putBoolean(goalId + "_achieved", goal.achieved);
            goalsEditor.putString(goalId + "_setDate", dateFormat.format(goal.setDate));
        }
        goalsEditor.apply();

        // Queue for Firebase sync if offline
        if (currentUser != null && !HabitUtils.isNetworkAvailable(this)) {
            queueChange("keys_and_labels", null);
        } else if (currentUser != null) {
            syncPendingChanges();
        }
    }

    private void saveSettingsToPreferences() {
        notifPrefs.edit()
                .putBoolean("sleep_mode_enabled", sleepModeEnabled)
                .putInt("sleep_start_hour", sleepStartHour)
                .putInt("sleep_start_minute", sleepStartMinute)
                .putInt("sleep_end_hour", sleepEndHour)
                .putInt("sleep_end_minute", sleepEndMinute)
                .apply();
        addictionPrefs.edit()
                .putBoolean("addiction_reminders_enabled", addictionRemindersEnabled)
                .putInt("addiction_reminder_hour", addictionReminderHour)
                .putInt("addiction_reminder_minute", addictionReminderMinute)
                .apply();

        // Queue settings sync if offline
        if (currentUser != null && !HabitUtils.isNetworkAvailable(this)) {
            queueChange("settings", null);
        } else if (currentUser != null) {
            syncPendingChanges();
        }
    }

    private void createOwnAccountabilityRequestCard(String requestId, String habit, int age, String gender, LinearLayout container) {
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

        TextView titleText = new TextView(this);
        titleText.setText(getString(R.string.your_request));
        titleText.setTextSize(18);
        titleText.setTypeface(null, Typeface.BOLD);
        layout.addView(titleText);

        TextView habitText = new TextView(this);
        habitText.setText(getString(R.string.accountability_for_habit, habit));
        habitText.setPadding(0, 8, 0, 0);
        layout.addView(habitText);

        TextView detailsText = new TextView(this);
        String genderDisplay = gender.substring(0, 1).toUpperCase() + gender.substring(1).toLowerCase();
        detailsText.setText(getString(R.string.user_details_format, age, genderDisplay));
        detailsText.setPadding(0, 8, 0, 16);
        layout.addView(detailsText);

        Button deleteButton = new Button(this);
        deleteButton.setText(R.string.cancel_request);
        deleteButton.setOnClickListener(v -> cancelAccountabilityRequest(requestId));
        layout.addView(deleteButton);

        container.addView(card);
    }

    private void createAccountabilityRequestCard(String requestId, String username, String habit, int age, String gender, LinearLayout container) {
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

        TextView titleText = new TextView(this);
        titleText.setText(getString(R.string.accountability_request_from, username));
        titleText.setTextSize(18);
        titleText.setTypeface(null, Typeface.BOLD);
        layout.addView(titleText);

        TextView habitText = new TextView(this);
        habitText.setText(getString(R.string.accountability_for_habit, habit));
        habitText.setPadding(0, 8, 0, 0);
        layout.addView(habitText);

        TextView detailsText = new TextView(this);
        String genderDisplay = gender.substring(0, 1).toUpperCase() + gender.substring(1).toLowerCase();
        detailsText.setText(getString(R.string.user_details_format, age, genderDisplay));
        detailsText.setPadding(0, 8, 0, 16);
        layout.addView(detailsText);

        // Fetch user's profile to determine if they can accept the request
        if (currentUser != null) {
            database.child("users").child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long userAgeLong = snapshot.child("age").getValue(Long.class);
                    String userGender = snapshot.child("gender").getValue(String.class);
                    int userAge = userAgeLong != null ? userAgeLong.intValue() : 0;

                    Button acceptButton = new Button(ResultsActivity.this);
                    acceptButton.setText(R.string.accept_request);
                    acceptButton.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    if (userAge == 0 || userGender == null || userGender.isEmpty()) {
                        acceptButton.setEnabled(false);
                        acceptButton.setText(R.string.complete_profile_to_accept);
                        acceptButton.setBackgroundTintList(ContextCompat.getColorStateList(ResultsActivity.this, R.color.gray));
                    } else {
                        acceptButton.setOnClickListener(v -> acceptAccountabilityRequest(requestId, username));
                    }
                    layout.addView(acceptButton);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error fetching user profile: " + error.getMessage());
                    Button acceptButton = new Button(ResultsActivity.this);
                    acceptButton.setText(R.string.error_loading_profile);
                    acceptButton.setEnabled(false);
                    acceptButton.setBackgroundTintList(ContextCompat.getColorStateList(ResultsActivity.this, R.color.gray));
                    layout.addView(acceptButton);
                }
            });
        } else {
            Button acceptButton = new Button(this);
            acceptButton.setText(R.string.login_to_accept);
            acceptButton.setEnabled(false);
            acceptButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.gray));
            layout.addView(acceptButton);
        }

        container.addView(card);
    }

    private void cancelAccountabilityRequest(String requestId) {
        database.child("accountability_requests").child(requestId).child("isActive").setValue(false)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, R.string.request_cancelled, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, R.string.request_cancellation_failed, Toast.LENGTH_SHORT).show());
    }

    private void showAccountabilityPartnerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.accountability_partner_title);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_accountability_request, null);
        Spinner habitSpinner = dialogView.findViewById(R.id.habit_spinner);

        List<String> habitOptions = new ArrayList<>();
        Map<String, String> habitKeyMap = new HashMap<>();

        for (String key : habitKeys) {
            habitOptions.add(getTextForKey(key));
            habitKeyMap.put(getTextForKey(key), key);
        }
        for (String key : addictionKeys) {
            habitOptions.add(getTextForKey(key));
            habitKeyMap.put(getTextForKey(key), key);
        }

        if (habitOptions.isEmpty()) {
            habitOptions.add(getString(R.string.no_habits_available));
        }

        ArrayAdapter<String> habitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, habitOptions);
        habitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        habitSpinner.setAdapter(habitAdapter);

        builder.setView(dialogView);
        builder.setPositiveButton(R.string.submit_request, (dialog, which) -> {
            if (currentUser == null) {
                Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show();
                return;
            }

            String selectedHabit = habitSpinner.getSelectedItem().toString();
            if (selectedHabit.equals(getString(R.string.no_habits_available))) {
                Toast.makeText(this, R.string.no_habits_to_select, Toast.LENGTH_SHORT).show();
                return;
            }

            database.child("users").child(currentUser.getUid()).addListenerForSingleValueEvent(
                    new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String username = snapshot.child("username").getValue(String.class);
                            Long ageLong = snapshot.child("age").getValue(Long.class);
                            int age = ageLong != null ? ageLong.intValue() : 0;
                            String gender = snapshot.child("gender").getValue(String.class);

                            if (username == null || username.isEmpty()) {
                                Toast.makeText(ResultsActivity.this, R.string.set_username_first, Toast.LENGTH_LONG).show();
                                showUsernameDialog();
                                return;
                            }

                            if (age == 0 || gender == null || gender.isEmpty()) {
                                Toast.makeText(ResultsActivity.this, R.string.profile_incomplete, Toast.LENGTH_LONG).show();
                                return;
                            }

                            // Check if user already has an active request
                            database.child("accountability_requests")
                                    .orderByChild("userId")
                                    .equalTo(currentUser.getUid())
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                                            String habitKey = habitKeyMap.get(selectedHabit);
                                            String requestId = database.child("accountability_requests").push().getKey();
                                            if (requestId == null) {
                                                Toast.makeText(ResultsActivity.this, R.string.request_creation_error, Toast.LENGTH_SHORT).show();
                                                return;
                                            }

                                            Map<String, Object> requestData = new HashMap<>();
                                            requestData.put("userId", currentUser.getUid());
                                            requestData.put("username", username);
                                            requestData.put("habit", selectedHabit);
                                            requestData.put("habitKey", habitKey);
                                            requestData.put("age", age);
                                            requestData.put("gender", gender);
                                            requestData.put("timestamp", ServerValue.TIMESTAMP);
                                            requestData.put("isActive", true);

                                            database.child("accountability_requests").child(requestId).setValue(requestData)
                                                    .addOnSuccessListener(aVoid -> Toast.makeText(ResultsActivity.this, R.string.request_submitted_success, Toast.LENGTH_SHORT).show())
                                                    .addOnFailureListener(e -> Toast.makeText(ResultsActivity.this, R.string.request_submission_failed, Toast.LENGTH_SHORT).show());
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        builder.setNegativeButton(R.string.cancel_button, null);
        builder.show();
    }

    private void acceptAccountabilityRequest(String requestId, String partnerUsername) {
        if (currentUser == null) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        database.child("users").child(userId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String username = snapshot.child("username").getValue(String.class);
                        if (username == null || username.isEmpty()) {
                            Toast.makeText(ResultsActivity.this, R.string.set_username_first, Toast.LENGTH_SHORT).show();
                            showUsernameDialog();
                            return;
                        }

                        database.child("accountability_requests").child(requestId).addListenerForSingleValueEvent(
                                new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot requestSnapshot) {
                                        if (!requestSnapshot.exists()) {
                                            Toast.makeText(ResultsActivity.this, R.string.request_not_found, Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        Boolean isActive = requestSnapshot.child("isActive").getValue(Boolean.class);
                                        if (!Boolean.TRUE.equals(isActive)) {
                                            Toast.makeText(ResultsActivity.this, R.string.request_inactive, Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        String requestUserId = requestSnapshot.child("userId").getValue(String.class);
                                        String habitKey = requestSnapshot.child("habitKey").getValue(String.class);

                                        if (requestUserId == null) {
                                            Toast.makeText(ResultsActivity.this, R.string.request_not_found, Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        String chatId = UUID.randomUUID().toString();
                                        String chatName = getString(R.string.accountability_chat_name, username, partnerUsername);

                                        Map<String, Object> chatData = new HashMap<>();
                                        chatData.put("name", chatName);
                                        chatData.put("type", "accountability");
                                        chatData.put("created_at", dateFormat.format(new Date()));
                                        chatData.put("members", Arrays.asList(userId, requestUserId));
                                        chatData.put("habitKey", habitKey);

                                        DatabaseReference chatRef = database.child("accountability_chats").child(chatId);
                                        chatRef.setValue(chatData).addOnSuccessListener(aVoid -> {
                                            // Deactivate the request
                                            database.child("accountability_requests").child(requestId).child("isActive").setValue(false);
                                            Toast.makeText(ResultsActivity.this, R.string.request_accepted, Toast.LENGTH_SHORT).show();
                                            // Start the chat activity
                                            Intent intent = new Intent(ResultsActivity.this, CommunityActivity.class);
                                            intent.putExtra("CHAT_ID", chatId);
                                            intent.putExtra("CHAT_NAME", chatName);
                                            intent.putExtra("IS_ACCOUNTABILITY", true);
                                            startActivity(intent);
                                        }).addOnFailureListener(e -> {
                                            Log.e(TAG, "Error creating chat: " + e.getMessage());
                                            Toast.makeText(ResultsActivity.this, R.string.chat_creation_failed, Toast.LENGTH_SHORT).show();
                                        });
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e(TAG, "Error fetching request: " + error.getMessage());
                                        Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error fetching user profile: " + error.getMessage());
                        Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showMessagesScreen() {
        if (!HabitUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.offline_messages_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        homeContainer.setVisibility(View.GONE);
        habitsContainer.setVisibility(View.GONE);
        addictionsContainer.setVisibility(View.GONE);
        messagesContainer.setVisibility(View.VISIBLE);
        settingsContainer.setVisibility(View.GONE);

        // Create tabs for switching between accountability partners and chats
        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button accountabilityTab = new Button(this);
        accountabilityTab.setText(R.string.accountability_partners_tab);
        accountabilityTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        accountabilityTab.setOnClickListener(v -> displayAccountabilityRequests());

        Button chatsTab = new Button(this);
        chatsTab.setText(R.string.chats_tab);
        chatsTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        chatsTab.setOnClickListener(v -> displayUserChats());

        tabLayout.addView(accountabilityTab);
        tabLayout.addView(chatsTab);

        // Wrap content in a ScrollView
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout scrollContainer = new LinearLayout(this);
        scrollContainer.setOrientation(LinearLayout.VERTICAL);
        scrollContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollContainer.addView(tabLayout);
        scrollView.addView(scrollContainer);
        messagesContainer.addView(scrollView);

        // Default view: accountability partners
        displayAccountabilityRequests();
    }

    private void addAccountabilityChatCard(DataSnapshot chatSnapshot, LinearLayout container) {
        String chatId = chatSnapshot.getKey();
        String chatName = chatSnapshot.child("name").getValue(String.class);
        String habitKey = chatSnapshot.child("habitKey").getValue(String.class);

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

        TextView title = new TextView(this);
        String displayName = chatName != null ? chatName : getString(R.string.accountability_chat_default);
        title.setText(displayName);
        title.setTextSize(18);
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        title.setTextColor(ContextCompat.getColor(this, isDarkTheme ? R.color.teal_200 : R.color.purple_500));
        layout.addView(title);

        // Add habit/addiction name
        TextView habitText = new TextView(this);
        if (habitKey != null && (habitKey.startsWith("custom_habit_") || habitKey.startsWith("custom_addiction_"))) {
            fetchCustomHabitLabel(habitKey, habitName -> habitText.setText(getString(R.string.habit_label, habitName != null ? habitName : getString(R.string.unknown_habit))));
        } else {
            String habitName = habitKey != null ? getTextForKey(habitKey) : getString(R.string.unknown_habit);
            habitText.setText(getString(R.string.habit_label, habitName));
        }
        habitText.setTextSize(14);
        habitText.setPadding(0, 4, 0, 4);
        layout.addView(habitText);

        Button openButton = new Button(this);
        openButton.setText(R.string.open_chat_button);
        openButton.setOnClickListener(v -> {
            Intent intent = new Intent(ResultsActivity.this, CommunityActivity.class);
            intent.putExtra("CHAT_ID", chatId);
            intent.putExtra("CHAT_NAME", displayName);
            intent.putExtra("IS_ACCOUNTABILITY", true);
            startActivity(intent);
        });
        layout.addView(openButton);

        container.addView(card);
    }

    private void displayAccountabilityRequests() {
        messagesContainer.removeAllViews();

        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button accountabilityTab = new Button(this);
        accountabilityTab.setText(R.string.accountability_partners_tab);
        accountabilityTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        accountabilityTab.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500));
        accountabilityTab.setTextColor(Color.WHITE);

        Button chatsTab = new Button(this);
        chatsTab.setText(R.string.chats_tab);
        chatsTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        chatsTab.setOnClickListener(v -> displayUserChats());

        tabLayout.addView(accountabilityTab);
        tabLayout.addView(chatsTab);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout scrollContainer = new LinearLayout(this);
        scrollContainer.setOrientation(LinearLayout.VERTICAL);
        scrollContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollContainer.addView(tabLayout);
        scrollView.addView(scrollContainer);
        messagesContainer.addView(scrollView);

        TextView requestsHeader = new TextView(this);
        requestsHeader.setText(R.string.accountability_requests_header);
        requestsHeader.setTextSize(18);
        requestsHeader.setTypeface(null, Typeface.BOLD);
        requestsHeader.setPadding(32, 16, 32, 16);
        scrollContainer.addView(requestsHeader);

        Button createRequestButton = new Button(this);
        createRequestButton.setText(R.string.create_accountability_request);
        createRequestButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        createRequestButton.setPadding(32, 16, 32, 16);
        createRequestButton.setOnClickListener(v -> showAccountabilityPartnerDialog());
        scrollContainer.addView(createRequestButton);

        if (currentUser == null) {
            TextView loginText = new TextView(this);
            loginText.setText(R.string.login_to_see_requests);
            loginText.setPadding(32, 16, 32, 16);
            scrollContainer.addView(loginText);
            return;
        }

        database.child("accountability_requests")
                .orderByChild("isActive")
                .equalTo(true)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        final int STATIC_VIEWS_COUNT = 3;
                        while (scrollContainer.getChildCount() > STATIC_VIEWS_COUNT) {
                            scrollContainer.removeViewAt(STATIC_VIEWS_COUNT);
                        }

                        if (!snapshot.exists() || !snapshot.hasChildren()) {
                            TextView noRequestsText = new TextView(ResultsActivity.this);
                            noRequestsText.setText(R.string.no_active_requests);
                            noRequestsText.setPadding(32, 16, 32, 16);
                            scrollContainer.addView(noRequestsText);
                        } else {
                            boolean showedOwnRequests = false;
                            String currentUserId = currentUser.getUid();

                            for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                                String userId = requestSnapshot.child("userId").getValue(String.class);
                                if (currentUserId.equals(userId)) {
                                    showedOwnRequests = true;
                                    String requestId = requestSnapshot.getKey();
                                    String habit = requestSnapshot.child("habit").getValue(String.class);
                                    Long ageLong = requestSnapshot.child("age").getValue(Long.class);
                                    int age = ageLong != null ? ageLong.intValue() : 0;
                                    String gender = requestSnapshot.child("gender").getValue(String.class);
                                    if (requestId != null && habit != null && gender != null) {
                                        createOwnAccountabilityRequestCard(requestId, habit, age, gender, scrollContainer);
                                    }
                                }
                            }

                            if (showedOwnRequests) {
                                TextView othersHeader = new TextView(ResultsActivity.this);
                                othersHeader.setText(R.string.others_requests_header);
                                othersHeader.setTextSize(18);
                                othersHeader.setTypeface(null, Typeface.BOLD);
                                othersHeader.setPadding(32, 16, 32, 16);
                                scrollContainer.addView(othersHeader);
                            }

                            boolean showedOtherRequests = false;
                            for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                                String userId = requestSnapshot.child("userId").getValue(String.class);
                                if (userId != null && userId.equals(currentUserId)) {
                                    continue;
                                }

                                String requestId = requestSnapshot.getKey();
                                String username = requestSnapshot.child("username").getValue(String.class);
                                String habit = requestSnapshot.child("habit").getValue(String.class);
                                Long ageLong = requestSnapshot.child("age").getValue(Long.class);
                                int age = ageLong != null ? ageLong.intValue() : 0;
                                String gender = requestSnapshot.child("gender").getValue(String.class);

                                if (requestId != null && username != null && habit != null && gender != null) {
                                    showedOtherRequests = true;
                                    createAccountabilityRequestCard(requestId, username, habit, age, gender, scrollContainer);
                                }
                            }

                            if (!showedOtherRequests && snapshot.exists() && snapshot.hasChildren()) {
                                TextView noOtherRequestsText = new TextView(ResultsActivity.this);
                                noOtherRequestsText.setText(R.string.no_other_requests);
                                noOtherRequestsText.setPadding(32, 16, 32, 16);
                                scrollContainer.addView(noOtherRequestsText);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error fetching accountability requests: " + error.getMessage());
                        Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private ValueEventListener userChatsListener;
    private ValueEventListener globalChatsListener;
    private ValueEventListener accountabilityChatsListener;
    private ValueEventListener userProfileListener;

    private void displayUserChats() {
        messagesContainer.removeAllViews();

        // Create tabs
        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button accountabilityTab = new Button(this);
        accountabilityTab.setText(R.string.accountability_partners_tab);
        accountabilityTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        accountabilityTab.setOnClickListener(v -> {
            removeChatListeners();
            displayAccountabilityRequests();
        });

        Button chatsTab = new Button(this);
        chatsTab.setText(R.string.chats_tab);
        chatsTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        chatsTab.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500));
        chatsTab.setTextColor(Color.WHITE);

        tabLayout.addView(accountabilityTab);
        tabLayout.addView(chatsTab);

        // Create ScrollView for chat list
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout scrollContainer = new LinearLayout(this);
        scrollContainer.setOrientation(LinearLayout.VERTICAL);
        scrollContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollContainer.addView(tabLayout);
        scrollView.addView(scrollContainer);
        messagesContainer.addView(scrollView);

        if (currentUser == null) {
            TextView emptyText = new TextView(this);
            emptyText.setText(R.string.no_chats_text);
            emptyText.setPadding(32, 32, 32, 32);
            emptyText.setTextSize(16);
            scrollContainer.addView(emptyText);
            return;
        }

        String userId = currentUser.getUid();

        // Fetch user's profile to get gender and age
        DatabaseReference userProfileRef = database.child("users").child(userId);
        userProfileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                String userGender = userSnapshot.child("gender").getValue(String.class);
                Long userAgeLong = userSnapshot.child("age").getValue(Long.class);
                Integer userAge = userAgeLong != null ? userAgeLong.intValue() : null;

                // Fetch user-specific chats
                DatabaseReference userChatsRef = database.child("users").child(userId).child("chats");
                DatabaseReference globalChatsRef = database.child("chats");
                DatabaseReference accountabilityChatsRef = database.child("accountability_chats");

                // Clear existing chat views but keep tabs
                while (scrollContainer.getChildCount() > 1) {
                    scrollContainer.removeViewAt(1);
                }

                userChatsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userChatsSnapshot) {
                        updateChatList(userChatsSnapshot, globalChatsRef, accountabilityChatsRef, userGender, userAge, scrollContainer);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading user chats: " + error.getMessage());
                        Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
                    }
                };
                userChatsRef.addValueEventListener(userChatsListener);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading user profile: " + error.getMessage());
                Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
            }
        };
        userProfileRef.addListenerForSingleValueEvent(userProfileListener);
    }

    private void updateChatList(DataSnapshot userChatsSnapshot, DatabaseReference globalChatsRef,
                                DatabaseReference accountabilityChatsRef, String userGender,
                                Integer userAge, LinearLayout scrollContainer) {
        globalChatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot globalChatsSnapshot) {
                accountabilityChatsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot accountabilityChatsSnapshot) {
                        // Clear existing chat views but keep tabs
                        while (scrollContainer.getChildCount() > 1) {
                            scrollContainer.removeViewAt(1);
                        }

                        boolean hasChats = userChatsSnapshot.hasChildren() ||
                                globalChatsSnapshot.hasChildren() ||
                                accountabilityChatsSnapshot.hasChildren();
                        if (!hasChats) {
                            TextView emptyText = new TextView(ResultsActivity.this);
                            emptyText.setText(R.string.no_chats_text);
                            emptyText.setPadding(32, 32, 32, 32);
                            emptyText.setTextSize(16);
                            scrollContainer.addView(emptyText);
                            return;
                        }

                        // Process user-specific chats
                        if (userChatsSnapshot.exists()) {
                            for (DataSnapshot chatSnapshot : userChatsSnapshot.getChildren()) {
                                if (canAccessChat(chatSnapshot, userGender, userAge)) {
                                    addChatCard(chatSnapshot, false, scrollContainer);
                                }
                            }
                        }

                        // Process global chats
                        if (globalChatsSnapshot.exists()) {
                            for (DataSnapshot chatSnapshot : globalChatsSnapshot.getChildren()) {
                                if (canAccessChat(chatSnapshot, userGender, userAge)) {
                                    addChatCard(chatSnapshot, true, scrollContainer);
                                }
                            }
                        }

                        // Process accountability chats
                        if (accountabilityChatsSnapshot.exists()) {
                            String userId = currentUser.getUid();
                            for (DataSnapshot chatSnapshot : accountabilityChatsSnapshot.getChildren()) {
                                List<String> members = new ArrayList<>();
                                if (chatSnapshot.child("members").exists()) {
                                    for (DataSnapshot memberSnapshot : chatSnapshot.child("members").getChildren()) {
                                        String memberId = memberSnapshot.getValue(String.class);
                                        if (memberId != null) {
                                            members.add(memberId);
                                        }
                                    }
                                }
                                if (members.contains(userId)) {
                                    addAccountabilityChatCard(chatSnapshot, scrollContainer);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading accountability chats: " + error.getMessage());
                        Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
                    }
                };
                accountabilityChatsRef.addValueEventListener(accountabilityChatsListener);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading global chats: " + error.getMessage());
                Toast.makeText(ResultsActivity.this, R.string.firebase_load_error, Toast.LENGTH_SHORT).show();
            }
        };
        globalChatsRef.addValueEventListener(globalChatsListener);
    }

    private void removeChatListeners() {
        if (userProfileListener != null) {
            database.child("users").child(currentUser.getUid()).removeEventListener(userProfileListener);
            userProfileListener = null;
        }
        if (userChatsListener != null) {
            database.child("users").child(currentUser.getUid()).child("chats").removeEventListener(userChatsListener);
            userChatsListener = null;
        }
        if (globalChatsListener != null) {
            database.child("chats").removeEventListener(globalChatsListener);
            globalChatsListener = null;
        }
        if (accountabilityChatsListener != null) {
            database.child("accountability_chats").removeEventListener(accountabilityChatsListener);
            accountabilityChatsListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeChatListeners();
        streakUpdateRunnables.values().forEach(streakUpdateHandler::removeCallbacks);
    }

    private void showSettingsScreen() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.settings_dialog_title));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(30, 20, 30, 20);

        // Notifications Section
        TextView notificationsTitle = new TextView(this);
        notificationsTitle.setText(getString(R.string.notifications_title));
        notificationsTitle.setTextSize(18);
        notificationsTitle.setTypeface(null, Typeface.BOLD);
        notificationsTitle.setPadding(0, 0, 0, 10);
        mainLayout.addView(notificationsTitle);

        LinearLayout notificationsLayout = new LinearLayout(this);
        notificationsLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(notificationsLayout);

        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch sleepModeSwitch = new Switch(this);
        sleepModeSwitch.setText(getString(R.string.sleep_mode_switch));
        sleepModeSwitch.setChecked(sleepModeEnabled);
        sleepModeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            sleepModeEnabled = isChecked;
            saveSettingsToPreferences();
            rescheduleAllNotifications();
            updateSleepTimeLayoutVisibility(notificationsLayout, isChecked);
            if (currentUser != null && HabitUtils.isNetworkAvailable(this)) {
                saveSettingsToFirebase();
            }
        });
        notificationsLayout.addView(sleepModeSwitch);

        LinearLayout sleepTimeLayout = createSleepTimeLayout();
        notificationsLayout.addView(sleepTimeLayout);
        updateSleepTimeLayoutVisibility(notificationsLayout, sleepModeEnabled);

        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch addictionRemindersSwitch = new Switch(this);
        addictionRemindersSwitch.setText(getString(R.string.addiction_reminders_switch));
        addictionRemindersSwitch.setChecked(addictionRemindersEnabled);
        addictionRemindersSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            addictionRemindersEnabled = isChecked;
            saveSettingsToPreferences();
            if (isChecked) {
                updateAddictionReminders();
            } else {
                cancelNotification("all_addictions");
                Toast.makeText(this, getString(R.string.addiction_reminders_disabled_toast), Toast.LENGTH_SHORT).show();
            }
            if (currentUser != null && HabitUtils.isNetworkAvailable(this)) {
                saveSettingsToFirebase();
            }
        });
        notificationsLayout.addView(addictionRemindersSwitch);

        TextView addictionReminderTimeLabel = new TextView(this);
        addictionReminderTimeLabel.setText(getString(R.string.addiction_reminder_time_label));
        addictionReminderTimeLabel.setTextSize(16);
        addictionReminderTimeLabel.setPadding(0, 20, 0, 0);
        notificationsLayout.addView(addictionReminderTimeLabel);

        Button addictionReminderTimeButton = new Button(this);
        addictionReminderTimeButton.setText(String.format(Locale.getDefault(), "%02d:%02d", addictionReminderHour, addictionReminderMinute));
        addictionReminderTimeButton.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            addictionReminderHour = hour;
            addictionReminderMinute = minute;
            addictionReminderTimeButton.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
            saveSettingsToPreferences();
            if (addictionRemindersEnabled) {
                updateAddictionReminders();
            }
            if (currentUser != null && HabitUtils.isNetworkAvailable(this)) {
                saveSettingsToFirebase();
            }
        }, addictionReminderHour, addictionReminderMinute, true).show());
        notificationsLayout.addView(addictionReminderTimeButton);

        // App Info Section
        TextView appInfoTitle = new TextView(this);
        appInfoTitle.setText(getString(R.string.app_info_title));
        appInfoTitle.setTextSize(18);
        appInfoTitle.setTypeface(null, Typeface.BOLD);
        appInfoTitle.setPadding(0, 30, 0, 10);
        mainLayout.addView(appInfoTitle);

        TextView versionText = new TextView(this);
        try {
            versionText.setText(getString(R.string.version_label) + " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception e) {
            versionText.setText(getString(R.string.version_unknown));
        }
        mainLayout.addView(versionText);

        Button feedbackButton = new Button(this);
        feedbackButton.setText(getString(R.string.feedback_button));
        feedbackButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:email@alexpechkin.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject));
            try {
                startActivity(Intent.createChooser(intent, getString(R.string.feedback_button)));
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.no_email_app_error), Toast.LENGTH_SHORT).show();
            }
        });
        mainLayout.addView(feedbackButton);

        // Account Section
        TextView accountTitle = new TextView(this);
        accountTitle.setText(getString(R.string.account_title));
        accountTitle.setTextSize(18);
        accountTitle.setTypeface(null, Typeface.BOLD);
        accountTitle.setPadding(0, 30, 0, 10);
        mainLayout.addView(accountTitle);

        Button logoutButton = new Button(this);
        logoutButton.setText(getString(R.string.logout_button));
        logoutButton.setOnClickListener(v -> showLogoutConfirmationDialog());
        mainLayout.addView(logoutButton);

        scrollView.addView(mainLayout);
        builder.setView(scrollView);
        builder.setPositiveButton(getString(R.string.close_button), null);
        builder.show();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_confirmation_title))
                .setMessage(getString(R.string.logout_confirmation_message))
                .setPositiveButton(getString(R.string.yes_button), (dialog, which) -> performLogout())
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show();
    }

    private void performLogout() {
        // Initialize Google Sign-In client
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        for (String key : habitKeys) {
            cancelNotification(key);
        }
        cancelNotification("all_addictions");

        // Sign out from Google
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut();

            // Clear all SharedPreferences
            sharedPrefs.edit().clear().apply();
            statsPrefs.edit().clear().apply();
            notifPrefs.edit().clear().apply();
            addictionPrefs.edit().clear().apply();
            goalsPrefs.edit().clear().apply();
            pendingChangesPrefs.edit().clear().apply();

            // Clear in-memory data
            habitKeys.clear();
            addictionKeys.clear();
            habitStats.clear();
            addictionStats.clear();
            goals.clear();

            // Redirect to MainActivity
            Intent intent = new Intent(ResultsActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            Toast.makeText(this, getString(R.string.logout_success_toast), Toast.LENGTH_SHORT).show();
        });
    }
}