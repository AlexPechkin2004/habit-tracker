package com.alexpechkin.habittracker;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int RC_SIGN_IN = 9001;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private SignInButton signInButton;
    private Button nextButton;
    private EditText nameEditText;
    private EditText ageEditText;
    private Spinner genderSpinner;
    private boolean isInitialCheck = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cleanupForFreshInstall();

        try {
            FirebaseApp.initializeApp(this);
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Помилка отримання FirebaseAuth instance: ", e);
            mAuth = null;
        }

        setContentView(R.layout.activity_main);

        try {
            mDatabase = FirebaseDatabase.getInstance("https://habittracker-83359-default-rtdb.europe-west1.firebasedatabase.app").getReference();
        } catch (Exception e) {
            Log.e(TAG, "Помилка отримання Firebase Database: ", e);
            Toast.makeText(this, getString(R.string.database_connection_error), Toast.LENGTH_SHORT).show();
        }

        nameEditText = findViewById(R.id.nameEditText);
        ageEditText = findViewById(R.id.ageEditText);
        genderSpinner = findViewById(R.id.genderSpinner);
        signInButton = findViewById(R.id.signInButton);
        nextButton = findViewById(R.id.nextButton);

        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(this,
                R.array.gender_array, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);

        genderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                checkNextButtonState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                checkNextButtonState();
            }
        });

        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkNextButtonState();
            }
        };

        nameEditText.addTextChangedListener(inputWatcher);
        ageEditText.addTextChangedListener(inputWatcher);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // Обов'язково для Firebase
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        signInButton.setOnClickListener(v -> signIn());

        nextButton.setOnClickListener(v -> {
            if (mAuth != null && mAuth.getCurrentUser() != null) {
                FirebaseUser user = mAuth.getCurrentUser();
                String userId = user.getUid();
                String userName = nameEditText.getText().toString().trim();
                String ageText = ageEditText.getText().toString().trim();

                int ageValue;
                try {
                    ageValue = Integer.parseInt(ageText);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, getString(R.string.invalid_age_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (ageValue < 14 || ageValue > 90) {
                    Toast.makeText(MainActivity.this, getString(R.string.age_range_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                // Отримуємо позицію вибраного елемента зі Spinner
                int selectedPosition = genderSpinner.getSelectedItemPosition();
                if (selectedPosition == 0) {
                    Toast.makeText(MainActivity.this, getString(R.string.select_gender_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                String gender;
                if (selectedPosition == 1) {
                    gender = "male";
                } else {
                    gender = "female";
                }

                // Зберігаємо дані користувача в Firebase і SharedPreferences
                saveUserDataToFirebase(userId, userName, ageValue, gender);

            } else {
                Toast.makeText(MainActivity.this, getString(R.string.login_first_error), Toast.LENGTH_SHORT).show();
                updateUI(null);
            }
        });

        if (mAuth != null) {
            updateUI(mAuth.getCurrentUser());
        } else {
            updateUI(null);
        }
    }


    private void cleanupForFreshInstall() {
        SharedPreferences installPrefs = getSharedPreferences("app_install", MODE_PRIVATE);
        boolean isFirstRun = installPrefs.getBoolean("is_first_run", true);

        if (isFirstRun) {
            Log.d(TAG, "Перший запуск додатку після встановлення - очищення SharedPreferences");

            getSharedPreferences("user_data", MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("HabitTrackerPrefs", MODE_PRIVATE).edit().clear().apply();

            installPrefs.edit().putBoolean("is_first_run", false).apply();
        }
    }

    private boolean checkUserCompletedSetup() {
        FirebaseUser currentUser = null;
        try {
            if (mAuth != null) {
                currentUser = mAuth.getCurrentUser();
            }
        } catch (Exception e) {
            Log.e(TAG, "Помилка отримання інформації про користувача Firebase: ", e);
            return false;
        }

        if (currentUser == null) {
            Log.d(TAG, "Користувач не авторизований");
            return false;
        }

        SharedPreferences sp = getSharedPreferences("user_data", MODE_PRIVATE);
        String name = sp.getString("name", null);
        int age = sp.getInt("age", 0);
        String gender = sp.getString("gender", null);

        if (name == null || age <= 0 || gender == null) {
            Log.d(TAG, "Відсутні персональні дані користувача");
            return false;
        }

        SharedPreferences habitsPrefs = getSharedPreferences("HabitTrackerPrefs", MODE_PRIVATE);
        Set<String> selectedHabits = habitsPrefs.getStringSet("selectedHabits", null);
        Set<String> selectedAddictions = habitsPrefs.getStringSet("selectedAddictions", null);

        boolean hasSelectedItems = (selectedHabits != null && !selectedHabits.isEmpty()) ||
                (selectedAddictions != null && !selectedAddictions.isEmpty());

        Log.d(TAG, "Перевірка вибраних звичок/залежностей: " + hasSelectedItems);
        return hasSelectedItems;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (isInitialCheck && checkUserCompletedSetup()) {
            Log.d(TAG, "onStart: Переходимо до ResultsActivity, бо всі дані заповнені");
            startActivity(new Intent(MainActivity.this, ResultsActivity.class));
            finish();
            return;
        }

        isInitialCheck = false;

        if (mAuth != null) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            updateUI(currentUser);
            if (currentUser != null) {
                Log.d(TAG, "Користувач вже увійшов у Firebase: " + currentUser.getEmail());
            } else {
                Log.d(TAG, "Користувач не увійшов у Firebase.");
            }
        } else {
            Log.w(TAG, "Firebase Auth не ініціалізовано в onStart.");
            updateUI(null);
        }
    }

    private void signIn() {
        Log.d(TAG, "Починаємо процес Google Sign-In...");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "Отримано результат від Google Sign-In (requestCode=" + requestCode + ")");
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(@NonNull Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.i(TAG, "Google Sign-In успішний. Email: " + account.getEmail());
            if (mAuth != null) {
                Log.d(TAG, "Починаємо Firebase Auth з Google.");
                firebaseAuthWithGoogle(account.getIdToken());
            } else {
                Log.e(TAG, "Firebase Auth не ініціалізовано!");
                Toast.makeText(this, getString(R.string.firebase_not_configured_error), Toast.LENGTH_SHORT).show();
                updateUI(null);
            }
        } catch (ApiException e) {
            Log.w(TAG, "Google Sign-In не вдався, код помилки: " + e.getStatusCode(), e);

            if (e.getStatusCode() == 7) {
                Toast.makeText(MainActivity.this, getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this,
                        getString(R.string.error_sign_in_failed, e.getStatusCode()),
                        Toast.LENGTH_SHORT).show();
            }

            updateUI(null);

        } catch (Exception e) {
            Log.e(TAG, "Неочікувана помилка при обробці результату Google Sign-In", e);
            Toast.makeText(MainActivity.this, getString(R.string.unexpected_error), Toast.LENGTH_SHORT).show();
            updateUI(null);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null) {
            Log.e(TAG, "firebaseAuthWithGoogle: idToken is null!");
            Toast.makeText(this, getString(R.string.null_token_error), Toast.LENGTH_SHORT).show();
            updateUI(null);
            return;
        }
        Log.d(TAG, "Отримання Firebase credential з Google idToken...");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.i(TAG, "Firebase signInWithCredential: успіх");
                        FirebaseUser user = mAuth.getCurrentUser();
                        boolean isNewUser = Objects.requireNonNull(task.getResult().getAdditionalUserInfo()).isNewUser();
                        Log.d(TAG, "Це новий користувач Firebase: " + isNewUser);
                        assert user != null;
                        Toast.makeText(MainActivity.this,
                                getString(R.string.login_success, user.getEmail()),
                                Toast.LENGTH_SHORT).show();

                        if (!isNewUser) {
                            checkUserDataInFirebase(user.getUid());
                        } else {
                            updateUI(user);
                        }
                    } else {
                        Log.w(TAG, "Firebase signInWithCredential: помилка", task.getException());
                        Toast.makeText(MainActivity.this,
                                getString(R.string.firebase_auth_error),
                                Toast.LENGTH_SHORT).show();
                        signOutGoogleOnly();
                        updateUI(null);
                    }
                });
    }

    private void checkUserDataInFirebase(String userId) {
        Log.d(TAG, "Перевірка існуючих даних користувача в Firebase: " + userId);

        DatabaseReference userRef = mDatabase.child("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                FirebaseUser currentUser = mAuth.getCurrentUser();

                if (dataSnapshot.exists()) {
                    Log.d(TAG, "Знайдено дані користувача в Firebase");

                    boolean hasUserData = dataSnapshot.child("name").exists() &&
                            dataSnapshot.child("age").exists() &&
                            dataSnapshot.child("gender").exists();

                    boolean hasHabits = dataSnapshot.child("habits").exists();
                    boolean hasAddictions = dataSnapshot.child("addictions").exists();

                    if (hasUserData && (hasHabits || hasAddictions)) {
                        Log.d(TAG, "Користувач має всі необхідні дані");

                        String name = dataSnapshot.child("name").getValue(String.class);
                        Long ageLong = dataSnapshot.child("age").getValue(Long.class);
                        int age = ageLong != null ? ageLong.intValue() : 0;
                        String gender = dataSnapshot.child("gender").getValue(String.class);

                        SharedPreferences sp = getSharedPreferences("user_data", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putString("name", name);
                        editor.putInt("age", age);
                        editor.putString("gender", gender);
                        editor.apply();

                        SharedPreferences habitsPrefs = getSharedPreferences("HabitTrackerPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor habitsEditor = habitsPrefs.edit();

                        Set<String> habitKeys = new HashSet<>();
                        Set<String> addictionKeys = new HashSet<>();

                        if (hasHabits) {
                            for (DataSnapshot habitSnapshot : dataSnapshot.child("habits").getChildren()) {
                                String habitKey = habitSnapshot.getValue(String.class);
                                if (habitKey != null) {
                                    habitKeys.add(habitKey);
                                }
                            }
                            habitsEditor.putStringSet("selectedHabits", habitKeys);
                        }

                        if (hasAddictions) {
                            for (DataSnapshot addictionSnapshot : dataSnapshot.child("addictions").getChildren()) {
                                String addictionKey = addictionSnapshot.getValue(String.class);
                                if (addictionKey != null) {
                                    addictionKeys.add(addictionKey);
                                }
                            }
                            habitsEditor.putStringSet("selectedAddictions", addictionKeys);
                        }

                        if (dataSnapshot.child("habit_labels").exists()) {
                            for (DataSnapshot labelSnapshot : dataSnapshot.child("habit_labels").getChildren()) {
                                String key = labelSnapshot.getKey();
                                String value = labelSnapshot.getValue(String.class);
                                if (key != null && value != null) {
                                    habitsEditor.putString(key, value);
                                }
                            }
                        }

                        if (dataSnapshot.child("addiction_labels").exists()) {
                            for (DataSnapshot labelSnapshot : dataSnapshot.child("addiction_labels").getChildren()) {
                                String key = labelSnapshot.getKey();
                                String value = labelSnapshot.getValue(String.class);
                                if (key != null && value != null) {
                                    habitsEditor.putString(key, value);
                                }
                            }
                        }

                        habitsEditor.apply();

                        startActivity(new Intent(MainActivity.this, ResultsActivity.class));
                        finish();
                    } else {
                        Log.d(TAG, "У користувача відсутні деякі необхідні дані");
                        updateUI(currentUser);
                    }
                } else {
                    Log.d(TAG, "Даних користувача не знайдено в Firebase");
                    updateUI(currentUser);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Помилка при отриманні даних користувача: " + databaseError.getMessage());
                Toast.makeText(MainActivity.this,
                        getString(R.string.user_data_check_error) + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();

                FirebaseUser currentUser = mAuth.getCurrentUser();
                updateUI(currentUser);
            }
        });
    }

    private void updateUI(@Nullable FirebaseUser user) {
        if (user != null) {
            Log.d(TAG, "updateUI: Користувач Firebase присутній (" + user.getEmail() + ")");
            signInButton.setVisibility(View.GONE);
            nameEditText.setVisibility(View.VISIBLE);
            ageEditText.setVisibility(View.VISIBLE);
            genderSpinner.setVisibility(View.VISIBLE);
            nextButton.setVisibility(View.VISIBLE);
            checkNextButtonState();
        } else {
            Log.d(TAG, "updateUI: Користувач Firebase відсутній (null)");
            signInButton.setVisibility(View.VISIBLE);
            nameEditText.setVisibility(View.GONE);
            ageEditText.setVisibility(View.GONE);
            genderSpinner.setVisibility(View.GONE);
            nextButton.setEnabled(false);
            nextButton.setVisibility(View.GONE);
        }
    }

    private void checkNextButtonState() {
        String name = nameEditText.getText().toString().trim();
        String ageText = ageEditText.getText().toString().trim();
        boolean isAgeValid = false;
        if (!ageText.isEmpty()) {
            try {
                int ageVal = Integer.parseInt(ageText);
                if (ageVal >= 14 && ageVal <= 90) {
                    isAgeValid = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        boolean isGenderSelected = genderSpinner.getSelectedItemPosition() != 0;
        nextButton.setEnabled(!name.isEmpty() && isAgeValid && isGenderSelected);
    }

    private void signOutGoogleOnly() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Вихід з Google Sign-In успішний.");
            } else {
                Log.w(TAG, "Помилка виходу з Google Sign-In.", task.getException());
            }
            updateUI(null);
        });
    }

    private void saveUserDataToFirebase(String userId, String userName, int age, String gender) {
        DatabaseReference userRef = mDatabase.child("users").child(userId);

        userRef.child("name").setValue(userName);
        userRef.child("age").setValue(age);
        userRef.child("gender").setValue(gender).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Дані користувача збережено успішно: " + userName + ", age: " + age + ", gender: " + gender);
                Toast.makeText(MainActivity.this, getString(R.string.user_data_saved_success), Toast.LENGTH_SHORT).show();

                SharedPreferences sp = getSharedPreferences("user_data", MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.putString("name", userName);
                editor.putInt("age", age);
                editor.putString("gender", gender);
                editor.apply();

                Intent intent = new Intent(MainActivity.this, HabitsActivity.class);
                startActivity(intent);
            } else {
                Log.e(TAG, "Помилка збереження даних: ", task.getException());
                Toast.makeText(MainActivity.this, "Error saving data: " +
                        Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}