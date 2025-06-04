package com.alexpechkin.habittracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HabitsActivity extends AppCompatActivity {

    private final Set<String> selectedHabits = new HashSet<>();
    private SharedPreferences sharedPreferences;
    private final Map<String, String> habitKeyMap = new HashMap<>();

    private FirebaseUser currentUser;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_habits);

        sharedPreferences = getSharedPreferences("HabitTrackerPrefs", MODE_PRIVATE);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance("https://habittracker-83359-default-rtdb.europe-west1.firebasedatabase.app").getReference();

        LinearLayout habitsLayout = findViewById(R.id.habitsLayout);
        Button nextButton = findViewById(R.id.nextButton);
        Button addCustomHabitButton = findViewById(R.id.addCustomHabitButton);
        EditText customHabitEditText = findViewById(R.id.customHabitEditText);

        addCustomHabitButton.setText(getString(R.string.add_custom));

        String[][] habitsWithKeys = {
                {"healthy_sleep", getString(R.string.healthy_sleep)},
                {"morning_exercises", getString(R.string.morning_exercises)},
                {"reading", getString(R.string.reading)},
                {"meditation", getString(R.string.meditation)}
        };

        for (String[] habitInfo : habitsWithKeys) {
            String habitKey = habitInfo[0];
            String habitText = habitInfo[1];

            habitKeyMap.put(habitText, habitKey);

            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(habitText);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedHabits.add(habitKey);
                } else {
                    selectedHabits.remove(habitKey);
                }
                nextButton.setEnabled(!selectedHabits.isEmpty());
            });
            habitsLayout.addView(checkBox);
        }

        addCustomHabitButton.setOnClickListener(v -> {
            String customHabit = customHabitEditText.getText().toString().trim();
            if (!customHabit.isEmpty()) {
                String customKey = "custom_habit_" + System.currentTimeMillis();
                habitKeyMap.put(customHabit, customKey);

                CheckBox checkBox = new CheckBox(HabitsActivity.this);
                checkBox.setText(customHabit);
                checkBox.setChecked(true);
                selectedHabits.add(customKey);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedHabits.add(customKey);
                    } else {
                        selectedHabits.remove(customKey);
                    }
                    nextButton.setEnabled(!selectedHabits.isEmpty());
                });
                habitsLayout.addView(checkBox);
                customHabitEditText.setText("");

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(customKey, customHabit);
                editor.apply();

                nextButton.setEnabled(true);
            }
        });

        nextButton.setOnClickListener(v -> {
            saveHabitsToSharedPreferences();
            saveHabitsToFirebase();
        });
    }

    private void saveHabitsToSharedPreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet("selectedHabits", selectedHabits);

        for (Map.Entry<String, String> entry : habitKeyMap.entrySet()) {
            if (!entry.getValue().startsWith("custom_habit_")) {
                editor.putString(entry.getValue(), entry.getKey());
            }
        }

        editor.apply();
    }

    private void saveHabitsToFirebase() {
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.user_not_authorized_error), Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        DatabaseReference userRef = mDatabase.child("users").child(userId);

        userRef.child("habits").setValue(new ArrayList<>(selectedHabits))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> customHabitLabels = new HashMap<>();
                        for (String habitKey : selectedHabits) {
                            if (habitKey.startsWith("custom_habit_")) {
                                customHabitLabels.put(habitKey, sharedPreferences.getString(habitKey, ""));
                            }
                        }

                        if (!customHabitLabels.isEmpty()) {
                            userRef.child("habit_labels").setValue(customHabitLabels);
                        }

                        Toast.makeText(HabitsActivity.this, getString(R.string.habits_saved_success), Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(HabitsActivity.this, AddictionsActivity.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(HabitsActivity.this, getString(R.string.habits_save_error), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Optional: Force light or dark mode, or follow system
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("selectedHabits", new ArrayList<>(selectedHabits));
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<String> savedHabits = savedInstanceState.getStringArrayList("selectedHabits");
        if (savedHabits != null) {
            selectedHabits.addAll(savedHabits);

            LinearLayout habitsLayout = findViewById(R.id.habitsLayout);
            for (int i = 0; i < habitsLayout.getChildCount(); i++) {
                CheckBox checkBox = (CheckBox) habitsLayout.getChildAt(i);
                String habitKey = habitKeyMap.get(checkBox.getText().toString());
                checkBox.setChecked(selectedHabits.contains(habitKey));
            }
        }
    }
}