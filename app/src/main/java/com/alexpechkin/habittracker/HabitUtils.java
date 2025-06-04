package com.alexpechkin.habittracker;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.appcompat.app.AppCompatActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class HabitUtils {
    public static Date getDefaultReminderTime() {
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

    public static Date getTomorrow() {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        return tomorrow.getTime();
    }

    public static int[] calculateHabitStreaks(ResultsActivity.HabitStats stats) {
        int currentStreak = 0;
        int longestStreak = 0;

        if (stats.completionTimestamps.isEmpty()) {
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
                try {
                    Calendar currentDay = Calendar.getInstance();
                    currentDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i))));
                    Calendar nextDay = Calendar.getInstance();
                    nextDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i + 1))));

                    long daysDiff = TimeUnit.MILLISECONDS.toDays(nextDay.getTimeInMillis() - currentDay.getTimeInMillis());
                    if (daysDiff == 1) {
                        currentStreak++;
                    } else {
                        break;
                    }
                } catch (ParseException ignored) {
                }
            }
        } else if (!completionDays.isEmpty()) {
            try {
                Calendar lastCompletionDay = Calendar.getInstance();
                lastCompletionDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(completionDays.size() - 1))));
                long daysSinceLast = TimeUnit.MILLISECONDS.toDays(today.getTimeInMillis() - lastCompletionDay.getTimeInMillis());
                if (daysSinceLast <= 1) {
                    currentStreak = 1;
                    for (int i = completionDays.size() - 2; i >= 0; i--) {
                        Calendar currentDay = Calendar.getInstance();
                        currentDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i))));
                        Calendar nextDay = Calendar.getInstance();
                        nextDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i + 1))));

                        long daysDiff = TimeUnit.MILLISECONDS.toDays(nextDay.getTimeInMillis() - currentDay.getTimeInMillis());
                        if (daysDiff == 1) {
                            currentStreak++;
                        } else {
                            break;
                        }
                    }
                }
            } catch (ParseException e) {
                // Handle parsing error
            }
        }

        int tempStreak = 1;
        for (int i = 0; i < completionDays.size() - 1; i++) {
            try {
                Calendar currentDay = Calendar.getInstance();
                currentDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i))));
                Calendar nextDay = Calendar.getInstance();
                nextDay.setTime(Objects.requireNonNull(fmt.parse(completionDays.get(i + 1))));

                long daysDiff = TimeUnit.MILLISECONDS.toDays(nextDay.getTimeInMillis() - currentDay.getTimeInMillis());
                if (daysDiff == 1) {
                    tempStreak++;
                } else {
                    longestStreak = Math.max(longestStreak, tempStreak);
                    tempStreak = 1;
                }
            } catch (ParseException e) {
                // Handle parsing error
            }
        }
        longestStreak = Math.max(longestStreak, tempStreak);

        return new int[]{currentStreak, longestStreak};
    }

    public static String formatDuration(long millis, AppCompatActivity context) {
        if (millis <= 0) return "0 " + context.getString(R.string.days_label);

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days).append(" ").append(context.getString(days == 1 ? R.string.day_singular : R.string.days_label)).append(" ");
        }
        if (hours > 0 || days > 0) {
            result.append(hours).append(" ").append(context.getString(hours == 1 ? R.string.hour_singular : R.string.hours_label)).append(" ");
        }
        result.append(minutes).append(" ").append(context.getString(minutes == 1 ? R.string.minute_singular : R.string.minutes_label));
        return result.toString().trim();
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }
}