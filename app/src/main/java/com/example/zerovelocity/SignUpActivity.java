package com.example.zerovelocity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private EditText etDisplayName, etEmail, etPassword, etConfirmPassword;
    private Button btnSignUp, btnChooseProfilePicture;
    private ImageView ivProfilePicture;
    private TextView tvGoToLogin, tvProfilePictureError;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private StorageReference profilePicturesRef;
    private Uri selectedProfilePictureUri;
    private Uri cameraProfilePictureUri;
    private ActivityResultLauncher<String> profilePicturePicker;
    private ActivityResultLauncher<Uri> cameraPictureLauncher;

    // Init firebase image pickers form fields and button actions
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // Init firebase auth and Realtime Database
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/");
        dbRef = database.getReference("users");
        profilePicturesRef = FirebaseStorage.getInstance().getReference("profilePictures");

        //Opens the phone gallery and returns the selected image URI
        profilePicturePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        setSelectedProfilePicture(uri);
                    }
                });

        //Opens the camera and saves the captured photo into the URI created by file provider
        cameraPictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraProfilePictureUri != null) {
                        setSelectedProfilePicture(cameraProfilePictureUri);
                    }
                });

        etDisplayName     = findViewById(R.id.et_display_name);
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        ivProfilePicture  = findViewById(R.id.iv_profile_picture);
        btnChooseProfilePicture = findViewById(R.id.btn_choose_profile_picture);
        tvProfilePictureError = findViewById(R.id.tv_profile_picture_error);
        btnSignUp         = findViewById(R.id.btn_sign_up);
        tvGoToLogin       = findViewById(R.id.tv_go_to_login);

        btnChooseProfilePicture.setOnClickListener(v -> showProfilePictureOptions());

        btnSignUp.setOnClickListener(v -> attemptSignUp());

        tvGoToLogin.setOnClickListener(v -> finish());
    }

    // validates the form and creates a new firebase auth account for new user
    private void attemptSignUp() {
        String displayName     = etDisplayName.getText().toString().trim();
        String email           = etEmail.getText().toString().trim();
        String password        = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        clearErrors();

        if (!validateForm(displayName, email, password, confirmPassword)) {
            return;
        }

        setLoading(true);
        String normalizedDisplayName = displayName.toLowerCase();
        createAccount(displayName, normalizedDisplayName, email, password);
    }

    private void checkUsernameAndContinue(String displayName, String normalizedDisplayName, String email) {
        // Query after auth sign up so the database request satisfies auth != null rules clean up code upon failing to remove auth account if unsucceful signup
        // this still prevents dupe names such as "Test" and "test" in friend search
        dbRef.orderByChild("displayNameLowercase").equalTo(normalizedDisplayName).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        cleanupCreatedUserAfterFailure();
                        etDisplayName.setError("Username is already taken");
                        etDisplayName.requestFocus();
                        setLoading(false);
                    } else {
                        uploadProfilePicture(displayName, normalizedDisplayName, email);
                    }
                })
                .addOnFailureListener(e -> {
                    //method called to clean up auth accounts if failed
                    cleanupCreatedUserAfterFailure();
                    setLoading(false);
                    Toast.makeText(this, "Could not check username: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // checks all required fields before firebase auth creates the account
    private boolean validateForm(String displayName, String email, String password, String confirmPassword) {
        boolean valid = true;

        if (selectedProfilePictureUri == null) {
            tvProfilePictureError.setText("Profile picture is required");
            tvProfilePictureError.setVisibility(View.VISIBLE);
            valid = false;
        }

        if (TextUtils.isEmpty(displayName)) {
            etDisplayName.setError("Username is required");
            valid = false;
        } else if (displayName.length() < 3 || displayName.length() > 20) {
            etDisplayName.setError("Username must be 3-20 characters");
            valid = false;
        } else if (!displayName.matches("^[A-Za-z0-9_]+$")) {
            etDisplayName.setError("Use only letters, numbers and underscores");
            valid = false;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address");
            valid = false;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            valid = false;
        } else if (!isStrongPassword(password)) {
            etPassword.setError("Use 8+ chars with uppercase, lowercase and a number");
            valid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError("Confirm your password");
            valid = false;
        } else if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            valid = false;
        }

        return valid;
    }

    // method to validate password it must meet the apps minimum strength rule before firebase sign up
    private boolean isStrongPassword(String password) {
        return password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*");
    }

    // Lets the user choose either the camera or an existing gallery image
    private void showProfilePictureOptions() {
        String[] options = {"Take photo", "Choose from gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Profile picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openCamera();
                    } else {
                        profilePicturePicker.launch("image/*");
                    }
                })
                .show();
    }

    // creates a private cache file and shares it with the camera app through file provider
    private void openCamera() {
        File imageDir = new File(getCacheDir(), "images");
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
            return;
        }

        File imageFile = new File(imageDir, "profile_picture_" + System.currentTimeMillis() + ".jpg");
        cameraProfilePictureUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                imageFile);
        cameraPictureLauncher.launch(cameraProfilePictureUri);
    }

    // stores the selected image URI and updates the preview shown on the sign up form
    private void setSelectedProfilePicture(Uri uri) {
        selectedProfilePictureUri = uri;
        ivProfilePicture.setPadding(0, 0, 0, 0);
        ivProfilePicture.setImageURI(uri);
        tvProfilePictureError.setVisibility(View.GONE);
    }

    // creates the firebase auth user only after local validation and username uniqueness pass
    private void createAccount(String displayName, String normalizedDisplayName, String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        checkUsernameAndContinue(displayName, normalizedDisplayName, email);
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Sign up failed: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Uploads the mandatory profile image under profilePictures/{uid} in firebase storage
    private void uploadProfilePicture(String displayName, String normalizedDisplayName, String email) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || selectedProfilePictureUri == null) {
            setLoading(false);
            Toast.makeText(this, "Sign up failed. Try again.", Toast.LENGTH_LONG).show();
            return;
        }

        StorageReference imageRef = profilePicturesRef.child(user.getUid());
        imageRef.putFile(selectedProfilePictureUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return imageRef.getDownloadUrl();
                })
                .addOnSuccessListener(photoUrl ->
                        updateAuthProfile(displayName, normalizedDisplayName, email, photoUrl))
                .addOnFailureListener(e -> {
                    cleanupCreatedUserAfterFailure();
                    setLoading(false);
                    Toast.makeText(this, "Profile picture upload failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // Adds the display name and uploaded image URL to firebase auths user profile
    private void updateAuthProfile(String displayName, String normalizedDisplayName, String email, Uri photoUrl) {
        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .setPhotoUri(photoUrl)
                .build();

        mAuth.getCurrentUser().updateProfile(profileUpdate)
                .addOnSuccessListener(aVoid -> saveUserToDatabase(displayName, normalizedDisplayName, email, photoUrl.toString()))
                .addOnFailureListener(e -> {
                    cleanupCreatedUserAfterFailure();
                    setLoading(false);
                    Toast.makeText(this, "Profile setup failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // Writes the new users profile, username index field, image URL, and starting totals
    private void saveUserToDatabase(String displayName, String normalizedDisplayName, String email, String photoUrl) {
        String uid = mAuth.getCurrentUser().getUid();

        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("displayName", displayName);
        user.put("displayNameLowercase", normalizedDisplayName);
        user.put("email", email);
        user.put("profilePictureUrl", photoUrl);
        user.put("totalDrinks", 0);
        user.put("totalVapes", 0);
        user.put("totalCigarettes", 0);
        user.put("createdAt", System.currentTimeMillis());

        dbRef.child(uid).setValue(user)
                .addOnSuccessListener(aVoid -> navigateToMain())
                .addOnFailureListener(e -> {
                    cleanupCreatedUserAfterFailure();
                    setLoading(false);
                    Toast.makeText(this, "Profile save failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // clears previous validation messages before re checking the form
    private void clearErrors() {
        etDisplayName.setError(null);
        etEmail.setError(null);
        etPassword.setError(null);
        etConfirmPassword.setError(null);
        tvProfilePictureError.setVisibility(View.GONE);
    }

    // Prevents double submitting while auth and storage and Database writes are in progress
    private void setLoading(boolean loading) {
        btnSignUp.setEnabled(!loading);
        btnChooseProfilePicture.setEnabled(!loading);
        btnSignUp.setText(loading ? "Creating account..." : "Create Account");
    }

    // Removes the auth account if image upload/profile save fails after account creation
    private void cleanupCreatedUserAfterFailure() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.delete();
        }
    }

    // Opens the main app screen and clears the auth screens from the back stack
    private void navigateToMain() {
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
