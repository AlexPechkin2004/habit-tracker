package com.alexpechkin.habittracker;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class BaseStatsActivity extends AppCompatActivity {

    protected static final String TAG = "BaseStatsActivity"; // Subclasses can override if needed

    protected DatabaseReference database;
    protected FirebaseUser currentUser;
    protected SharedPreferences statsPrefs;
    protected SharedPreferences sharedPrefs; // For item names
    protected String itemKey;
    protected ResultsActivity.HabitStats stats;

    protected final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    protected final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    protected final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    protected final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

    protected GridLayout calendarGrid;
    protected TextView monthYearText;
    protected BarChart dayOfWeekChart;
    protected BarChart timeOfDayChart;
    protected TextView currentStreakText;
    protected TextView longestStreakText;
    protected TextView startDateText;
    protected TextView progressText;

    protected LinearLayout eventHistorySection;
    protected TextView eventHistoryTitleText;
    protected LinearLayout eventListContainer;

    protected Date selectedDateForDetails = null;
    protected Calendar displayedMonthCalendar = Calendar.getInstance();

    private final Handler streakUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable streakUpdateRunnable;

    // --- Abstract methods to be implemented by subclasses ---
    protected abstract int getLayoutResourceId();
    protected abstract String getIntentItemKeyName(); // e.g., "ADDICTION_KEY" or "HABIT_KEY"
    protected abstract String getFirebaseStatsNode(); // e.g., "addiction_stats" or "habit_stats"
    protected abstract void initializeSpecificUiElements(); // To find specific title, event list, etc.
    protected abstract String getItemDisplayName(); // To set the screen title
    protected abstract void updateStreakDisplayTexts();
    protected abstract void styleCalendarDay(TextView dayView, Date dayDate, boolean isFutureDay);
    protected abstract void onCalendarDayClicked(Date dayDate);
    protected abstract List<Date> getChartDataSourceList();
    protected abstract int getPrimaryChartColor();
    protected abstract String getChartDataLabel();
    protected abstract String getNoChartDataText();
    protected abstract String getEventHistoryTitleForDate(Date date);
    protected abstract void populateEventListForDate(List<Date> eventsOnSelectedDate);
    protected abstract String getNoEventsOnDateText();
    protected abstract int getEventItemPrimaryColor();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResourceId());

        initializeCoreVariables();
        if (itemKey == null || itemKey.isEmpty()) {
            Toast.makeText(this, "Error: Item key not provided.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        initializeCommonUiElements();
        initializeSpecificUiElements(); // Subclass specific UI
        setTitleText();

        loadStatisticsData();
        startStreakUpdater();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (streakUpdateRunnable != null) {
            streakUpdateHandler.removeCallbacks(streakUpdateRunnable);
        }
    }

    private void initializeCoreVariables() {
        database = FirebaseDatabase.getInstance().getReference();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        statsPrefs = getSharedPreferences("StatsPrefs", MODE_PRIVATE);
        sharedPrefs = getSharedPreferences("HabitsPrefs", MODE_PRIVATE); // For names
        itemKey = getIntent().getStringExtra(getIntentItemKeyName());
    }

    private void initializeCommonUiElements() {
        calendarGrid = findViewById(R.id.calendar_grid);
        monthYearText = findViewById(R.id.month_year_text);
        dayOfWeekChart = findViewById(R.id.day_of_week_chart);
        timeOfDayChart = findViewById(R.id.time_of_day_chart);

        currentStreakText = findViewById(R.id.current_streak);
        longestStreakText = findViewById(R.id.longest_streak);
        startDateText = findViewById(R.id.start_date);
        progressText = findViewById(R.id.progress);

        findViewById(R.id.prev_month).setOnClickListener(v -> changeDisplayedMonth(-1));
        findViewById(R.id.next_month).setOnClickListener(v -> changeDisplayedMonth(1));

        updateMonthControlsVisibility();
        monthYearText.setText(monthYearFormat.format(displayedMonthCalendar.getTime()));
    }

    private void setTitleText() {
        // This assumes a TextView with id `item_title` exists in both layouts
        // If IDs are different (e.g. R.id.addiction_title, R.id.habit_title),
        // this should be handled in initializeSpecificUiElements() of subclasses.
        // For now, let's assume a common ID or it's handled by the abstract getItemDisplayName()
        // and set in the subclass's initializeSpecificUiElements.
        // Example: TextView screenTitle = findViewById(R.id.screen_title_textview); screenTitle.setText(getItemDisplayName());
    }


    private void loadStatisticsData() {
        if (currentUser != null && HabitUtils.isNetworkAvailable(this)) {
            loadDataFromFirebase(currentUser.getUid());
        } else {
            Toast.makeText(this, R.string.offline_mode_message, Toast.LENGTH_SHORT).show();
            loadDataFromLocalStorage();
        }
    }

    private void loadDataFromFirebase(String userId) {
        database.child("users").child(userId).child(getFirebaseStatsNode()).child(itemKey)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        stats = parseStatsObject(snapshot);
                        onStatsDataLoaded();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Firebase load error for " + itemKey + ": " + error.getMessage());
                        Toast.makeText(BaseStatsActivity.this, getString(R.string.firebase_load_error), Toast.LENGTH_SHORT).show();
                        loadDataFromLocalStorage(); // Fallback to local
                    }
                });
    }

    private void loadDataFromLocalStorage() {
        stats = getStatsObjectFromPreferences(itemKey);
        onStatsDataLoaded();
    }

    private ResultsActivity.HabitStats parseStatsObject(DataSnapshot snapshot) {
        ResultsActivity.HabitStats parsedStats = new ResultsActivity.HabitStats();
        try {
            String startDateStr = snapshot.child("start_date").getValue(String.class);
            parsedStats.startDate = (startDateStr != null) ? dateFormat.parse(startDateStr) : new Date();

            Long progressVal = snapshot.child("progress").getValue(Long.class);
            parsedStats.progress = (progressVal != null) ? progressVal.intValue() : 0;

            String nextReminderStr = snapshot.child("next_reminder").getValue(String.class);
            parsedStats.nextReminder = (nextReminderStr != null) ? dateFormat.parse(nextReminderStr) : HabitUtils.getDefaultReminderTime();

            String lastCheckStr = snapshot.child("last_check_date").getValue(String.class);
            parsedStats.lastCheckDate = (lastCheckStr != null) ? dateFormat.parse(lastCheckStr) : parsedStats.startDate;

            Long reminderIntervalVal = snapshot.child("reminder_interval").getValue(Long.class);
            parsedStats.reminderInterval = (reminderIntervalVal != null) ? reminderIntervalVal.intValue() : 1;

            Long reminderHourVal = snapshot.child("reminder_hour").getValue(Long.class);
            parsedStats.reminderHour = (reminderHourVal != null) ? reminderHourVal.intValue() : 18;

            Long reminderMinuteVal = snapshot.child("reminder_minute").getValue(Long.class);
            parsedStats.reminderMinute = (reminderMinuteVal != null) ? reminderMinuteVal.intValue() : 0;

            parsedStats.markedDays = new ArrayList<>();
            if (snapshot.child("marked_days").exists()) {
                for (DataSnapshot daySnap : snapshot.child("marked_days").getChildren()) {
                    String dateStr = daySnap.getValue(String.class);
                    if (dateStr != null) parsedStats.markedDays.add(dateOnlyFormat.parse(dateStr));
                }
            }

            parsedStats.relapseDates = new ArrayList<>();
            if (snapshot.child("relapse_dates").exists()) {
                for (DataSnapshot relapseSnap : snapshot.child("relapse_dates").getChildren()) {
                    String dateStr = relapseSnap.getValue(String.class);
                    if (dateStr != null) parsedStats.relapseDates.add(dateFormat.parse(dateStr));
                }
            }

            parsedStats.completionTimestamps = new ArrayList<>();
            if (snapshot.child("completion_timestamps").exists()) {
                for (DataSnapshot tsSnap : snapshot.child("completion_timestamps").getChildren()) {
                    String tsStr = tsSnap.getValue(String.class);
                    if (tsStr != null) parsedStats.completionTimestamps.add(dateFormat.parse(tsStr));
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing stats for " + itemKey + " from Firebase: " + e.getMessage());
            // Initialize with defaults if parsing fails
            parsedStats.startDate = new Date();
            parsedStats.nextReminder = HabitUtils.getDefaultReminderTime();
        }
        return parsedStats;
    }

    private ResultsActivity.HabitStats getStatsObjectFromPreferences(String key) {
        ResultsActivity.HabitStats prefsStats = new ResultsActivity.HabitStats();
        try {
            String startDateStr = statsPrefs.getString(key + "_start_date", null);
            prefsStats.startDate = (startDateStr != null) ? dateFormat.parse(startDateStr) : new Date();

            prefsStats.progress = statsPrefs.getInt(key + "_progress", 0);

            String nextReminderStr = statsPrefs.getString(key + "_next_reminder", null);
            prefsStats.nextReminder = (nextReminderStr != null) ? dateFormat.parse(nextReminderStr) : HabitUtils.getTomorrow();

            String lastCheckStr = statsPrefs.getString(key + "_last_check_date", null);
            prefsStats.lastCheckDate = (lastCheckStr != null) ? dateFormat.parse(lastCheckStr) : prefsStats.startDate;

            prefsStats.reminderInterval = statsPrefs.getInt(key + "_reminder_interval", 1);
            prefsStats.reminderHour = statsPrefs.getInt(key + "_reminder_hour", 18);
            prefsStats.reminderMinute = statsPrefs.getInt(key + "_reminder_minute", 0);

            prefsStats.markedDays = new ArrayList<>();
            Set<String> markedDaysSet = statsPrefs.getStringSet(key + "_marked_days", new HashSet<>());
            for (String dateStr : markedDaysSet) prefsStats.markedDays.add(dateOnlyFormat.parse(dateStr));

            prefsStats.relapseDates = new ArrayList<>();
            Set<String> relapseDatesSet = statsPrefs.getStringSet(key + "_relapse_dates", new HashSet<>());
            for (String dateStr : relapseDatesSet) prefsStats.relapseDates.add(dateFormat.parse(dateStr));

            prefsStats.completionTimestamps = new ArrayList<>();
            Set<String> completionTimestampsSet = statsPrefs.getStringSet(key + "_completion_timestamps", new HashSet<>());
            for (String tsStr : completionTimestampsSet) prefsStats.completionTimestamps.add(dateFormat.parse(tsStr));

        } catch (ParseException e) {
            Log.e(TAG, "Error parsing stats for " + key + " from SharedPreferences: " + e.getMessage());
            prefsStats.startDate = new Date();
            prefsStats.nextReminder = HabitUtils.getTomorrow();
        }
        return prefsStats;
    }

    protected void onStatsDataLoaded() {
        if (stats == null) {
            Log.e(TAG, "Stats object is null after loading for " + itemKey);
            Toast.makeText(this, "Could not load statistics.", Toast.LENGTH_SHORT).show();
            stats = new ResultsActivity.HabitStats(); // Ensure stats is not null
            stats.startDate = new Date(); // Default start date
        }
        displayBasicStatsInfo();
        updateStreakDisplayTexts(); // Abstracted to subclass
        renderCalendar();
        renderCharts();
        // Ensure streak updater picks up new stats
        streakUpdateHandler.removeCallbacks(streakUpdateRunnable);
        streakUpdateHandler.post(streakUpdateRunnable);
    }

    private void displayBasicStatsInfo() {
        if (stats.startDate != null) {
            startDateText.setText(getString(R.string.start_date_label) + " " + displayDateFormat.format(stats.startDate));
        } else {
            startDateText.setText(getString(R.string.start_date_label) + " N/A");
        }
        progressText.setText(getString(R.string.progress_label) + " " + stats.progress + "%");
    }


    private void startStreakUpdater() {
        streakUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (stats != null) {
                    updateStreakDisplayTexts(); // Abstracted
                    Log.d(TAG, "Streak display updated for: " + itemKey);
                }
                streakUpdateHandler.postDelayed(this, 60000); // Update every minute
            }
        };
        streakUpdateHandler.post(streakUpdateRunnable); // Initial run
    }

    protected void renderCalendar() {
        calendarGrid.removeAllViews();
        calendarGrid.setColumnCount(7);
        // Row 0 for day names, Row 1-6 for days. Max 6 rows for days.
        calendarGrid.setRowCount(7); // 1 for day names + up to 6 for days

        String[] dayNames = getResources().getStringArray(R.array.day_names);
        for (int i = 0; i < dayNames.length; i++) {
            TextView dayNameView = new TextView(this);
            dayNameView.setText(dayNames[i]);
            dayNameView.setTextSize(14);
            dayNameView.setGravity(Gravity.CENTER);
            dayNameView.setPadding(8, 8, 8, 8);
            dayNameView.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(i, 1f);
            params.rowSpec = GridLayout.spec(0); // First row
            dayNameView.setLayoutParams(params);
            calendarGrid.addView(dayNameView);
        }

        Calendar calIter = (Calendar) displayedMonthCalendar.clone();
        calIter.set(Calendar.DAY_OF_MONTH, 1);
        calIter.set(Calendar.HOUR_OF_DAY, 0); calIter.set(Calendar.MINUTE, 0); calIter.set(Calendar.SECOND, 0); calIter.set(Calendar.MILLISECOND, 0);

        int firstDayOfMonthInWeek = calIter.get(Calendar.DAY_OF_WEEK); // SUNDAY=1, MONDAY=2...
        int offset = (firstDayOfMonthInWeek == Calendar.SUNDAY) ? 6 : firstDayOfMonthInWeek - Calendar.MONDAY; // Monday as start of week
        int daysInCurrentMonth = calIter.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0); todayCal.set(Calendar.MINUTE, 0); todayCal.set(Calendar.SECOND, 0); todayCal.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < offset; i++) { // Empty views for offset
            TextView emptyView = new TextView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(i, 1f);
            params.rowSpec = GridLayout.spec(1); // Second row
            emptyView.setLayoutParams(params);
            calendarGrid.addView(emptyView);
        }

        for (int dayNum = 1; dayNum <= daysInCurrentMonth; dayNum++) {
            calIter.set(Calendar.DAY_OF_MONTH, dayNum);
            final Date dayDate = calIter.getTime();
            boolean isFutureDay = calIter.after(todayCal);

            TextView dayView = new TextView(this);
            dayView.setText(String.valueOf(dayNum));
            dayView.setTextSize(14);
            dayView.setGravity(Gravity.CENTER);
            dayView.setPadding(10, 20, 10, 20); // Increased padding for touchability
            dayView.setTextColor(isFutureDay ? Color.LTGRAY : getThemeColor(com.google.android.material.R.attr.colorOnSurface));

            styleCalendarDay(dayView, dayDate, isFutureDay); // Abstracted

            if (!isFutureDay) {
                dayView.setOnClickListener(v -> {
                    selectedDateForDetails = dayDate;
                    onCalendarDayClicked(dayDate); // Abstracted
                    renderCalendar(); // Re-render to update selection highlight
                });
            }

            int currentGridRow = 1 + (offset + dayNum - 1) / 7;
            int currentGridCol = (offset + dayNum - 1) % 7;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(currentGridCol, 1f);
            params.rowSpec = GridLayout.spec(currentGridRow);
            dayView.setLayoutParams(params);
            calendarGrid.addView(dayView);
        }
    }


    protected void renderCharts() {
        if (stats == null) return;
        List<Date> chartData = getChartDataSourceList(); // Abstracted
        setupBarChart(dayOfWeekChart, chartData, false); // false for byHour
        setupBarChart(timeOfDayChart, chartData, true);  // true for byHour
    }

    private void setupBarChart(BarChart barChart, List<Date> eventDates, boolean byHour) {
        if (eventDates == null || eventDates.isEmpty()) {
            barChart.setNoDataText(getNoChartDataText()); // Abstracted
            barChart.invalidate();
            return;
        }

        int maxRange = byHour ? 24 : 7;
        int[] counts = new int[maxRange];
        Calendar cal = Calendar.getInstance();

        for (Date eventDate : eventDates) {
            cal.setTime(eventDate);
            int index;
            if (byHour) {
                index = cal.get(Calendar.HOUR_OF_DAY); // 0-23
            } else {
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // SUNDAY=1 ... SATURDAY=7
                index = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY; // Monday=0 ... Sunday=6
            }
            if (index >=0 && index < maxRange) {
                counts[index]++;
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < maxRange; i++) {
            entries.add(new BarEntry(i, counts[i]));
        }

        BarDataSet dataSet = new BarDataSet(entries, getChartDataLabel()); // Abstracted
        dataSet.setColor(ContextCompat.getColor(this, getPrimaryChartColor())); // Abstracted
        dataSet.setValueTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(byHour ? 0.4f : 0.3f);
        barChart.setData(barData);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(maxRange);
        xAxis.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        if (byHour) {
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%02d", (int) value);
                }
            });
        } else {
            final String[] dayNames = getResources().getStringArray(R.array.day_names); // Mon-Sun
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int index = (int) value;
                    return (index >= 0 && index < dayNames.length) ? dayNames[index] : "";
                }
            });
        }

        barChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setGranularity(1.0f);
        barChart.getAxisLeft().setGranularityEnabled(true);
        barChart.getAxisLeft().setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        barChart.getAxisLeft().setDrawGridLines(true);

        Legend legend = barChart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setYOffset(10f);
        legend.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));

        barChart.getDescription().setEnabled(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.setTouchEnabled(true);
        barChart.setPinchZoom(false);
        barChart.invalidate();
    }


    protected void changeDisplayedMonth(int delta) {
        Calendar newMonthCal = (Calendar) displayedMonthCalendar.clone();
        newMonthCal.add(Calendar.MONTH, delta);
        newMonthCal.set(Calendar.DAY_OF_MONTH, 1); // Normalize to start of month

        Calendar currentMonthNatural = Calendar.getInstance();
        currentMonthNatural.set(Calendar.DAY_OF_MONTH, 1);
        currentMonthNatural.set(Calendar.HOUR_OF_DAY, 0); currentMonthNatural.set(Calendar.MINUTE, 0);
        currentMonthNatural.set(Calendar.SECOND, 0); currentMonthNatural.set(Calendar.MILLISECOND, 0);

        // Prevent navigating to future months
        if (newMonthCal.after(currentMonthNatural)) {
            return;
        }

        displayedMonthCalendar = newMonthCal;
        monthYearText.setText(monthYearFormat.format(displayedMonthCalendar.getTime()));
        updateMonthControlsVisibility();
        renderCalendar(); // Re-render calendar for new month
    }

    private void updateMonthControlsVisibility() {
        TextView nextMonthButton = findViewById(R.id.next_month);
        Calendar currentMonthStart = Calendar.getInstance();
        currentMonthStart.set(Calendar.DAY_OF_MONTH, 1);
        currentMonthStart.set(Calendar.HOUR_OF_DAY, 0); currentMonthStart.set(Calendar.MINUTE, 0);
        currentMonthStart.set(Calendar.SECOND, 0); currentMonthStart.set(Calendar.MILLISECOND, 0);

        Calendar displayedMonthStart = (Calendar) displayedMonthCalendar.clone();
        displayedMonthStart.set(Calendar.DAY_OF_MONTH, 1);
        displayedMonthStart.set(Calendar.HOUR_OF_DAY, 0); displayedMonthStart.set(Calendar.MINUTE, 0);
        displayedMonthStart.set(Calendar.SECOND, 0); displayedMonthStart.set(Calendar.MILLISECOND, 0);


        if (displayedMonthStart.equals(currentMonthStart) || displayedMonthStart.after(currentMonthStart)) {
            nextMonthButton.setVisibility(View.INVISIBLE);
        } else {
            nextMonthButton.setVisibility(View.VISIBLE);
        }
    }

    protected int getThemeColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    // Helper to check if a date is today
    protected boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) return false;
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}