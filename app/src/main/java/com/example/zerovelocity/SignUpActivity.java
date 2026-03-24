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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private EditText etDisplayName, etEmail, etPassword, etConfirmPassword;
    private Button btnSignUp;
    private TextView tvGoToLogin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        etDisplayName     = findViewById(R.id.et_display_name);
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnSignUp         = findViewById(R.id.btn_sign_up);
        tvGoToLogin       = findViewById(R.id.tv_go_to_login);

        btnSignUp.setOnClickListener(v -> attemptSignUp());

        tvGoToLogin.setOnClickListener(v -> finish());
    }
    //method called when sign up button is pressed
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
                                .addOnCompleteListener(p -> saveUserToFirestore(displayName, email));
                    } else {
                        Toast.makeText(this, "Sign up failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
    //method called to save user data to the Firebase db
    private void saveUserToFirestore(String displayName, String email) {
        String uid = mAuth.getCurrentUser().getUid();

        Map<String, Object> user = new HashMap<>();
        user.put("uid",         uid);
        user.put("displayName", displayName);
        user.put("email",       email);
        user.put("photoUrl",    "");
        user.put("createdAt",   System.currentTimeMillis());

        db.collection("users").document(uid).set(user)
                .addOnSuccessListener(a -> navigateToMain())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Profile save failed, continuing anyway", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                });
    }
    //method used to navigate to MainActivity View
    private void navigateToMain() {
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
