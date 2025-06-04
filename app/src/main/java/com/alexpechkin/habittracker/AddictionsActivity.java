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

public class AddictionsActivity extends AppCompatActivity {

    private final Set<String> selectedAddictions = new HashSet<>();
    private Set<String> selectedHabits = new HashSet<>();
    private SharedPreferences sharedPreferences;
    private Button finishButton;
    private final Map<String, String> addictionKeyMap = new HashMap<>();

    private FirebaseUser currentUser;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addictions);

        sharedPreferences = getSharedPreferences("HabitTrackerPrefs", MODE_PRIVATE);
        selectedHabits = sharedPreferences.getStringSet("selectedHabits", new HashSet<>());

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance("https://habittracker-83359-default-rtdb.europe-west1.firebasedatabase.app").getReference();

        LinearLayout addictionsLayout = findViewById(R.id.addictionsLayout);
        finishButton = findViewById(R.id.finishButton);
        Button returnToHabitsButton = findViewById(R.id.returnToHabitsButton);
        Button addCustomAddictionButton = findViewById(R.id.addCustomAddictionButton);
        EditText customAddictionEditText = findViewById(R.id.customAddictionEditText);

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

        updateFinishButtonState();

        for (String[] addictionInfo : addictionsWithKeys) {
            String addictionKey = addictionInfo[0];
            String addictionText = addictionInfo[1];
            addictionKeyMap.put(addictionText, addictionKey);

            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(addictionText);
            checkBox.setPadding(16, 8, 16, 8);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedAddictions.add(addictionKey);
                } else {
                    selectedAddictions.remove(addictionKey);
                }
                updateFinishButtonState();
            });
            addictionsLayout.addView(checkBox);
        }

        addCustomAddictionButton.setOnClickListener(v -> {
            String customAddiction = customAddictionEditText.getText().toString().trim();
            if (!customAddiction.isEmpty()) {
                String customKey = "custom_addiction_" + System.currentTimeMillis();
                addictionKeyMap.put(customAddiction, customKey);

                CheckBox checkBox = new CheckBox(AddictionsActivity.this);
                checkBox.setText(customAddiction);
                checkBox.setChecked(true);
                checkBox.setPadding(16, 8, 16, 8);
                selectedAddictions.add(customKey);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedAddictions.add(customKey);
                    } else {
                        selectedAddictions.remove(customKey);
                    }
                    updateFinishButtonState();
                });
                addictionsLayout.addView(checkBox);
                customAddictionEditText.setText("");

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(customKey, customAddiction);
                editor.apply();

                updateFinishButtonState();
            }
        });

        finishButton.setOnClickListener(v -> {
            saveAddictionsToSharedPreferences();
            saveAddictionsToFirebase();
        });

        returnToHabitsButton.setOnClickListener(v -> finish());
    }

    private void saveAddictionsToSharedPreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet("selectedAddictions", selectedAddictions);

        for (Map.Entry<String, String> entry : addictionKeyMap.entrySet()) {
            if (!entry.getValue().startsWith("custom_addiction_")) {
                editor.putString(entry.getValue(), entry.getKey());
            }
        }

        editor.apply();
    }

    private void saveAddictionsToFirebase() {
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.user_not_authorized_error), Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        DatabaseReference userRef = mDatabase.child("users").child(userId);

        userRef.child("addictions").setValue(new ArrayList<>(selectedAddictions))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Object> customAddictionLabels = new HashMap<>();
                        for (String addictionKey : selectedAddictions) {
                            if (addictionKey.startsWith("custom_addiction_")) {
                                customAddictionLabels.put(addictionKey, sharedPreferences.getString(addictionKey, ""));
                            }
                        }

                        if (!customAddictionLabels.isEmpty()) {
                            userRef.child("addiction_labels").setValue(customAddictionLabels);
                        }

                        Toast.makeText(AddictionsActivity.this, getString(R.string.addictions_saved_success), Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(AddictionsActivity.this, ResultsActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(AddictionsActivity.this, getString(R.string.addictions_save_error), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateFinishButtonState() {
        finishButton.setEnabled(!selectedHabits.isEmpty() || !selectedAddictions.isEmpty());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("selectedAddictions", new ArrayList<>(selectedAddictions));
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<String> savedAddictions = savedInstanceState.getStringArrayList("selectedAddictions");
        if (savedAddictions != null) {
            selectedAddictions.addAll(savedAddictions);
            LinearLayout addictionsLayout = findViewById(R.id.addictionsLayout);
            for (int i = 0; i < addictionsLayout.getChildCount(); i++) {
                CheckBox checkBox = (CheckBox) addictionsLayout.getChildAt(i);
                String addictionKey = addictionKeyMap.get(checkBox.getText().toString());
                checkBox.setChecked(selectedAddictions.contains(addictionKey));
            }
            updateFinishButtonState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}