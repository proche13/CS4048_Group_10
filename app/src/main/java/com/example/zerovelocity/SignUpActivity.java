package com.example.zerovelocity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private EditText etDisplayName, etEmail, etPassword, etConfirmPassword;
    private ImageView ivProfilePicture;
    private Button btnSignUp, btnChooseProfilePicture;
    private TextView tvGoToLogin, tvProfilePictureError;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private Uri selectedProfilePictureUri;
    private Uri cameraProfilePictureUri;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // Initialises Firebase, binds the sign-up form fields, and connects button actions.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerImageLaunchers();
        setContentView(R.layout.activity_sign_up);

        // Initialise Firebase Auth and Realtime Database
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/");
        dbRef = database.getReference("users");

        etDisplayName     = findViewById(R.id.et_display_name);
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        ivProfilePicture  = findViewById(R.id.iv_profile_picture);
        btnSignUp         = findViewById(R.id.btn_sign_up);
        btnChooseProfilePicture = findViewById(R.id.btn_choose_profile_picture);
        tvGoToLogin       = findViewById(R.id.tv_go_to_login);
        tvProfilePictureError = findViewById(R.id.tv_profile_picture_error);

        btnSignUp.setOnClickListener(v -> attemptSignUp());
        btnChooseProfilePicture.setOnClickListener(v -> showProfilePictureOptions());

        tvGoToLogin.setOnClickListener(v -> finish());
    }

    // Registers camera/gallery callbacks before the activity is started.
    private void registerImageLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        setProfilePicture(uri);
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraProfilePictureUri != null) {
                        setProfilePicture(cameraProfilePictureUri);
                    }
                });

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openCamera();
                    } else {
                        Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Lets the user pick either the camera or the gallery for the required profile photo.
    private void showProfilePictureOptions() {
        String[] options = {"Take a photo", "Choose from gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Profile picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCameraThenOpen();
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void requestCameraThenOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // Creates a cache file and shares it with the camera app through FileProvider.
    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "images");
            dir.mkdirs();
            File tmp = File.createTempFile("signup_profile_", ".jpg", dir);
            cameraProfilePictureUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    tmp);
            cameraLauncher.launch(cameraProfilePictureUri);
        } catch (IOException e) {
            Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
        }
    }

    // Stores the selected URI and updates the preview so the user knows it was accepted.
    private void setProfilePicture(Uri uri) {
        selectedProfilePictureUri = uri;
        ivProfilePicture.setImageURI(uri);
        tvProfilePictureError.setVisibility(TextView.GONE);
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
        if (selectedProfilePictureUri == null) {
            tvProfilePictureError.setText("Profile picture is required");
            tvProfilePictureError.setVisibility(TextView.VISIBLE);
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
        if (!isValidPassword(password)) {
            etPassword.setError("Use 8+ characters with uppercase, lowercase and a number");
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        uploadProfilePictureThenSaveUser(displayName, email);
                    } else {
                        Toast.makeText(this, "Sign up failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean isValidPassword(String password) {
        return password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*");
    }

    // Uploads the mandatory profile image before writing the user profile record.
    private void uploadProfilePictureThenSaveUser(String displayName, String email) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || selectedProfilePictureUri == null) {
            Toast.makeText(this, "Profile picture is required", Toast.LENGTH_LONG).show();
            return;
        }

        StorageReference profileImageRef = FirebaseStorage.getInstance()
                .getReference("profilePictures/" + user.getUid() + "/profile.jpg");

        profileImageRef.putFile(selectedProfilePictureUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return profileImageRef.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .setPhotoUri(downloadUri)
                            .build();
                    user.updateProfile(profileUpdate)
                            .addOnCompleteListener(p ->
                                    saveUserToDatabase(displayName, email, downloadUri.toString()));
                })
                .addOnFailureListener(e -> {
                    // The account is removed if the required image cannot be saved.
                    user.delete();
                    Toast.makeText(this, "Profile picture upload failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // Writes the new user's profile and starting totals into the Realtime Database.
    private void saveUserToDatabase(String displayName, String email, String profilePictureUrl) {
        String uid = mAuth.getCurrentUser().getUid();

        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("displayName", displayName);
        user.put("displayNameLowercase", displayName.toLowerCase(Locale.getDefault()));
        user.put("email", email);
        user.put("profilePictureUrl", profilePictureUrl);
        user.put("totalDrinks", 0);
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
