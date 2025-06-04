package com.alexpechkin.habittracker;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddictionStatsActivity extends BaseStatsActivity {

    private TextView addictionTitleTextView;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_addiction_stats;
    }

    @Override
    protected String getIntentItemKeyName() {
        return "ADDICTION_KEY"; // Key used in Intent
    }

    @Override
    protected String getFirebaseStatsNode() {
        return "addiction_stats";
    }

    @Override
    protected void initializeSpecificUiElements() {
        addictionTitleTextView = findViewById(R.id.addiction_title);
        addictionTitleTextView.setText(getItemDisplayName());

        eventHistorySection = findViewById(R.id.relapse_history_section);
        eventHistoryTitleText = findViewById(R.id.relapse_history_title);
        eventListContainer = findViewById(R.id.relapse_list_container);
        eventHistorySection.setVisibility(View.GONE); // Initially hidden
    }

    @Override
    protected String getItemDisplayName() {
        if (itemKey == null || itemKey.isEmpty()) return "Addiction";
        // Using sharedPrefs to get the display name, similar to original
        // This assumes sharedPrefs is initialized in BaseStatsActivity
        switch (itemKey) {
            case "alcohol": return getString(R.string.alcohol);
            case "cigarettes": return getString(R.string.cigarettes);
            case "drugs": return getString(R.string.drugs);
            case "gambling": return getString(R.string.gambling);
            case "self_harm": return getString(R.string.self_harm);
            case "pornography": return getString(R.string.pornography);
            case "social_networks": return getString(R.string.social_networks);
            case "sugar": return getString(R.string.sugar);
            // These seem like habits, but were in the original AddictionStatsActivity's getTextForKey
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

        long currentStreakMillis = (stats.relapseDates == null || stats.relapseDates.isEmpty()) ?
                System.currentTimeMillis() - stats.startDate.getTime() :
                System.currentTimeMillis() - Collections.max(stats.relapseDates).getTime();
        long longestStreakMillis = stats.calculateLongestStreakMillis(); // Assuming this method exists in HabitStats

        currentStreakText.setText(getString(R.string.current_streak_label) + " " + HabitUtils.formatDuration(currentStreakMillis, this));
        longestStreakText.setText(getString(R.string.longest_streak_label) + " " + HabitUtils.formatDuration(longestStreakMillis, this));
    }

    @Override
    protected void styleCalendarDay(TextView dayView, Date dayDate, boolean isFutureDay) {
        dayView.setBackgroundColor(Color.TRANSPARENT); // Default background

        if (isFutureDay) {
            dayView.setTextColor(Color.LTGRAY);
        } else {
            dayView.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        }

        if (stats != null && stats.markedDays != null) {
            for (Date markedDay : stats.markedDays) {
                if (isSameDay(markedDay, dayDate)) {
                    dayView.setBackgroundColor(ContextCompat.getColor(this, R.color.green)); // Or your green color
                    break;
                }
            }
        }
        if (stats != null && stats.relapseDates != null) {
            for (Date relapse : stats.relapseDates) {
                if (isSameDay(relapse, dayDate)) {
                    dayView.setBackgroundColor(ContextCompat.getColor(this, R.color.red)); // Or your red color
                    break; // Relapse color takes precedence
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

        List<Date> relapsesOnDate = new ArrayList<>();
        if (stats != null && stats.relapseDates != null) {
            for (Date relapse : stats.relapseDates) {
                if (isSameDay(relapse, dayDate)) {
                    relapsesOnDate.add(relapse);
                }
            }
        }
        populateEventListForDate(relapsesOnDate);
    }

    @Override
    protected List<Date> getChartDataSourceList() {
        return (stats != null) ? stats.relapseDates : new ArrayList<>();
    }

    @Override
    protected int getPrimaryChartColor() {
        return R.color.red; // Your red color for relapses
    }

    @Override
    protected String getChartDataLabel() {
        return getString(R.string.relapse_count_label);
    }

    @Override
    protected String getNoChartDataText() {
        return getString(R.string.no_relapse_data);
    }

    @Override
    protected String getEventHistoryTitleForDate(Date date) {
        return getString(R.string.relapse_history_title) + " " + displayDateFormat.format(date);
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
        return getString(R.string.no_relapses_text);
    }

    @Override
    protected int getEventItemPrimaryColor() {
        return R.color.red; // Or your specific red for relapse text
    }
}