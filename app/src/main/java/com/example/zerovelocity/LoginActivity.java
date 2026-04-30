package com.example.zerovelocity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoToSignUp;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail      = findViewById(R.id.et_email);
        etPassword   = findViewById(R.id.et_password);
        btnLogin     = findViewById(R.id.btn_login);
        tvGoToSignUp = findViewById(R.id.tv_go_to_sign_up);

        // Let the login screen draw before firebase auth starts its setup work
        // this helps prevents the launch window from sitting on a black screen
        btnLogin.setEnabled(false);
        View root = findViewById(android.R.id.content);
        root.post(() -> {
            mAuth = FirebaseAuth.getInstance();

            //sign out any existing session so login is always prompted on app open
            mAuth.signOut();
            btnLogin.setEnabled(true);
        });

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvGoToSignUp.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));
    }
    //method called when login button is pressed
    private void attemptLogin() {
        // The button should stay disabled until this is ready but this guard
        // prevents a crash if the user taps during startup
        if (mAuth == null) {
            Toast.makeText(this, "Login is still starting. Try again in a moment.", Toast.LENGTH_SHORT).show();
            return;
        }

        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        navigateToMain();
                    } else {
                        Toast.makeText(this, "Login failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
    //if sucessful login this is called to go to MainActivity View
    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
