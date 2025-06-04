package com.alexpechkin.habittracker;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HabitStatsActivity extends BaseStatsActivity {

    private TextView habitTitleTextView;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_habit_stats;
    }

    @Override
    protected String getIntentItemKeyName() {
        return "HABIT_KEY"; // Key used in Intent
    }

    @Override
    protected String getFirebaseStatsNode() {
        return "habit_stats";
    }

    @Override
    protected void initializeSpecificUiElements() {
        habitTitleTextView = findViewById(R.id.habit_title); // Assuming this ID in activity_habit_stats.xml
        habitTitleTextView.setText(getItemDisplayName());

        eventHistorySection = findViewById(R.id.completion_history_section);
        eventHistoryTitleText = findViewById(R.id.completion_history_title);
        eventListContainer = findViewById(R.id.completion_list_container);
        eventHistorySection.setVisibility(View.GONE); // Initially hidden
    }

    @Override
    protected String getItemDisplayName() {
        if (itemKey == null || itemKey.isEmpty()) return "Habit";
        switch (itemKey) {
            case "healthy_sleep": return getString(R.string.healthy_sleep);
            case "morning_exercises": return getString(R.string.morning_exercises);
            case "reading": return getString(R.string.reading);
            case "meditation": return getString(R.string.meditation);
            default:
                return sharedPrefs.getString(itemKey, itemKey); // Fallback to key or custom name
        }
    }

    @Override
    protected void updateStreakDisplayTexts() {
        if (stats == null || stats.startDate == null) {
            currentStreakText.setText(getString(R.string.current_streak_label) + " N/A");
            longestStreakText.setText(getString(R.string.longest_streak_label) + " N/A");
            return;
        }
        // Assuming HabitUtils.calculateHabitStreaks returns an array [current, longest]
        int[] streaks = HabitUtils.calculateHabitStreaks(stats);
        currentStreakText.setText(getString(R.string.current_streak_label) + " " + streaks[0] + " " + getString(R.string.days_label));
        longestStreakText.setText(getString(R.string.longest_streak_label) + " " + streaks[1] + " " + getString(R.string.days_label));
    }

    @Override
    protected void styleCalendarDay(TextView dayView, Date dayDate, boolean isFutureDay) {
        dayView.setBackgroundColor(Color.TRANSPARENT); // Default

        if (isFutureDay) {
            dayView.setTextColor(Color.LTGRAY);
        } else {
            dayView.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        }

        if (stats != null && stats.completionTimestamps != null) {
            for (Date completed : stats.completionTimestamps) {
                if (isSameDay(completed, dayDate)) {
                    dayView.setBackgroundColor(ContextCompat.getColor(this, R.color.green)); // Or your green color
                    break;
                }
            }
        }

        if (selectedDateForDetails != null && isSameDay(dayDate, selectedDateForDetails)) {
            dayView.setBackgroundColor(ContextCompat.getColor(this, R.color.blue)); // Or your blue selection color
        }
    }

    @Override
    protected void onCalendarDayClicked(Date dayDate) {
        eventHistorySection.setVisibility(View.VISIBLE);
        eventHistoryTitleText.setText(getEventHistoryTitleForDate(dayDate));

        List<Date> completionsOnDate = new ArrayList<>();
        if (stats != null && stats.completionTimestamps != null) {
            for (Date completion : stats.completionTimestamps) {
                if (isSameDay(completion, dayDate)) {
                    completionsOnDate.add(completion);
                }
            }
        }
        populateEventListForDate(completionsOnDate);
    }

    @Override
    protected List<Date> getChartDataSourceList() {
        return (stats != null) ? stats.completionTimestamps : new ArrayList<>();
    }

    @Override
    protected int getPrimaryChartColor() {
        return R.color.green; // Your green color for completions
    }

    @Override
    protected String getChartDataLabel() {
        return getString(R.string.completion_count_label);
    }

    @Override
    protected String getNoChartDataText() {
        return getString(R.string.no_completion_data);
    }

    @Override
    protected String getEventHistoryTitleForDate(Date date) {
        return getString(R.string.completion_history_title) + " " + displayDateFormat.format(date);
    }

    @Override
    protected void populateEventListForDate(List<Date> eventsOnSelectedDate) {
        eventListContainer.removeAllViews();
        if (eventsOnSelectedDate.isEmpty()) {
            TextView noEventsText = new TextView(this);
            noEventsText.setText(getNoEventsOnDateText());
            noEventsText.setPadding(16, 16, 16, 16);
            noEventsText.setTextSize(16);
            noEventsText.setGravity(Gravity.CENTER);
            eventListContainer.addView(noEventsText);
        } else {
            for (Date eventDate : eventsOnSelectedDate) {
                TextView eventView = new TextView(this);
                eventView.setText(dateFormat.format(eventDate)); // Full date and time
                eventView.setPadding(16, 8, 16, 8);
                eventView.setTextSize(14);
                eventView.setTextColor(ContextCompat.getColor(this, getEventItemPrimaryColor()));
                eventListContainer.addView(eventView);
            }
        }
    }

    @Override
    protected String getNoEventsOnDateText() {
        return getString(R.string.no_completions_text);
    }

    @Override
    protected int getEventItemPrimaryColor() {
        return R.color.green; // Or your specific green for completion text
    }
}