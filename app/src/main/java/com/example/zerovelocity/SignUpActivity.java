package com.example.zerovelocity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

import android.util.Log;
import com.google.firebase.auth.FirebaseUser;

public class SignUpActivity extends AppCompatActivity {

    private EditText etDisplayName, etEmail, etPassword, etConfirmPassword;
    private Button btnSignUp;
    private TextView tvGoToLogin;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    // Initialises Firebase, binds the sign-up form fields, and connects button actions.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // Initialise Firebase Auth and Realtime Database
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/");
        dbRef = database.getReference("users");

        etDisplayName     = findViewById(R.id.et_display_name);
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnSignUp         = findViewById(R.id.btn_sign_up);
        tvGoToLogin       = findViewById(R.id.tv_go_to_login);

        btnSignUp.setOnClickListener(v -> attemptSignUp());

        tvGoToLogin.setOnClickListener(v -> finish());
    }

    // Validates the form and creates a new Firebase Auth account for the user.
    private void attemptSignUp() {
        String displayName     = etDisplayName.getText().toString().trim();
        String email           = etEmail.getText().toString().trim();
        String password        = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(displayName)) {
            etDisplayName.setError("Display name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                                .setDisplayName(displayName)
                                .build();
                        mAuth.getCurrentUser().updateProfile(profileUpdate)
                                .addOnCompleteListener(p -> saveUserToDatabase(displayName, email));
                    } else {
                        Toast.makeText(this, "Sign up failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Writes the new user's profile and starting totals into the Realtime Database.
    private void saveUserToDatabase(String displayName, String email) {
        String uid = mAuth.getCurrentUser().getUid();

        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("displayName", displayName);
        user.put("email", email);
        user.put("totalDrinks", 0);
        user.put("totalVapes", 0);
        user.put("totalCigarettes", 0);
        user.put("createdAt", System.currentTimeMillis());

        dbRef.child(uid).setValue(user)
                .addOnSuccessListener(aVoid -> navigateToMain())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Profile save failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    navigateToMain();
                });
    }

    // Opens the main app screen and clears the auth screens from the back stack.
    private void navigateToMain() {
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
