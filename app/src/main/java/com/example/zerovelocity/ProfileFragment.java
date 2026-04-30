package com.example.zerovelocity;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private DatabaseReference rootRef;
    private StorageReference profilePicturesRef;
    private FirebaseUser currentUser;

    private EditText etDisplayName;
    private ImageView ivProfilePicture;
    private TextView tvEmail;
    private TextView tvFriendsCount;
    private TextView tvTotals;
    private TextView tvFriendsPreview;
    private Button btnChangeProfilePicture;

    private Uri cameraProfilePictureUri;
    private ActivityResultLauncher<String> profilePicturePicker;
    private ActivityResultLauncher<Uri> cameraPictureLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private ValueEventListener userListener;
    private ValueEventListener friendsListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //gallery picker used when the user chooses an existing profile image
        profilePicturePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadProfilePicture(uri);
                    }
                });

        //camera capture writes into the cache URI created by file provider
        cameraPictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraProfilePictureUri != null) {
                        uploadProfilePicture(cameraProfilePictureUri);
                    }
                });

        // requests camera permission before launching the camera from the profile page
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openCamera();
                    } else {
                        Toast.makeText(getContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    //inflates the profile UI and loads the signed in user and starts listening for profile data
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return view;
        }

        rootRef = FirebaseDatabase
                .getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference();
        profilePicturesRef = FirebaseStorage.getInstance().getReference("profilePictures");

        etDisplayName = view.findViewById(R.id.et_profile_display_name);
        ivProfilePicture = view.findViewById(R.id.iv_profile_picture);
        tvEmail = view.findViewById(R.id.tv_profile_email);
        tvFriendsCount = view.findViewById(R.id.tv_profile_friends_count);
        tvTotals = view.findViewById(R.id.tv_profile_totals);
        tvFriendsPreview = view.findViewById(R.id.tv_profile_friends_preview);
        Button btnSave = view.findViewById(R.id.btn_save_profile);
        btnChangeProfilePicture = view.findViewById(R.id.btn_change_profile_picture);
        Button btnChangePassword = view.findViewById(R.id.btn_change_password);
        Button btnLogout = view.findViewById(R.id.btn_logout);

        tvEmail.setText(currentUser.getEmail());
        btnSave.setOnClickListener(v -> saveProfile());
        btnChangeProfilePicture.setOnClickListener(v -> showProfilePictureOptions());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnLogout.setOnClickListener(v -> logout());

        listenToProfile();
        listenToFriends();

        return view;
    }

    //listens to the users profile record and updates the editable details and totals
    private void listenToProfile() {
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String displayName = snapshot.child("displayName").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String profilePictureUrl = snapshot.child("profilePictureUrl").getValue(String.class);
                Double totalDrinks = snapshot.child("totalDrinks").getValue(Double.class);
                Double totalCigarettes = snapshot.child("totalCigarettes").getValue(Double.class);

                if (!etDisplayName.hasFocus()) {
                    etDisplayName.setText(displayName != null ? displayName : "");
                }

                tvEmail.setText(email != null ? email : currentUser.getEmail());
                loadProfilePicture(profilePictureUrl);
                tvTotals.setText(String.format(
                        Locale.getDefault(),
                        "Drinks: %.0f  |  Cigarettes: %.0f",
                        totalDrinks != null ? totalDrinks : 0d,
                        totalCigarettes != null ? totalCigarettes : 0d
                ));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        };

        rootRef.child("users").child(currentUser.getUid()).addValueEventListener(userListener);
    }

    //listens to the users friends node and updates the friend count and preview text
    private void listenToFriends() {
        friendsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> names = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String displayName = child.child("displayName").getValue(String.class);
                    if (!TextUtils.isEmpty(displayName)) {
                        names.add(displayName);
                    }
                }

                tvFriendsCount.setText(String.valueOf(names.size()));
                tvFriendsPreview.setText(buildPreviewText(names, "No friends added yet"));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load friends", Toast.LENGTH_SHORT).show();
            }
        };

        rootRef.child("friends").child(currentUser.getUid()).addValueEventListener(friendsListener);
    }

    //builds a short comma separated preview of names for display on the profile page
    private String buildPreviewText(List<String> names, String emptyText) {
        if (names.isEmpty()) {
            return emptyText;
        }

        int limit = Math.min(names.size(), 5);
        return TextUtils.join(", ", names.subList(0, limit));
    }

    //saves an updated display name to both firebase auth and the users table
    private void saveProfile() {
        String updatedName = etDisplayName.getText().toString().trim();

        if (TextUtils.isEmpty(updatedName)) {
            etDisplayName.setError("Display name is required");
            return;
        }

        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(updatedName)
                .build();

        currentUser.updateProfile(request)
                .addOnSuccessListener(aVoid ->
                        rootRef.child("users").child(currentUser.getUid()).child("displayName").setValue(updatedName)
                                .addOnSuccessListener(value -> {
                                    Toast.makeText(getContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Saved to auth only", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show());
    }

    //lets the user update their profile image from either the camera or gallery
    private void showProfilePictureOptions() {
        String[] options = {"Take photo", "Choose from gallery"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Profile picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    } else {
                        profilePicturePicker.launch("image/*");
                    }
                })
                .show();
    }

    //creates a private cache file and shares it with the camera app through file provider
    private void openCamera() {
        File imageDir = new File(requireContext().getCacheDir(), "images");
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            Toast.makeText(getContext(), "Could not open camera", Toast.LENGTH_SHORT).show();
            return;
        }

        File imageFile = new File(imageDir, "profile_picture_" + System.currentTimeMillis() + ".jpg");
        try {
            cameraProfilePictureUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    imageFile);
            cameraPictureLauncher.launch(cameraProfilePictureUri);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Could not open camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    //uploads the replacement image and saves the new URL in auth and realtime database
    private void uploadProfilePicture(Uri imageUri) {
        if (currentUser == null || rootRef == null || profilePicturesRef == null) {
            return;
        }

        btnChangeProfilePicture.setEnabled(false);
        ivProfilePicture.setPadding(0, 0, 0, 0);
        ivProfilePicture.setImageURI(imageUri);

        StorageReference imageRef = profilePicturesRef.child(currentUser.getUid());
        imageRef.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return imageRef.getDownloadUrl();
                })
                .addOnSuccessListener(this::updateProfilePictureUrl)
                .addOnFailureListener(e -> {
                    btnChangeProfilePicture.setEnabled(true);
                    Toast.makeText(getContext(), "Failed to upload profile picture", Toast.LENGTH_LONG).show();
                });
    }

    private void updateProfilePictureUrl(Uri photoUrl) {
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setPhotoUri(photoUrl)
                .build();

        currentUser.updateProfile(request)
                .addOnSuccessListener(aVoid ->
                        rootRef.child("users").child(currentUser.getUid()).child("profilePictureUrl")
                                .setValue(photoUrl.toString())
                                .addOnSuccessListener(value -> {
                                    btnChangeProfilePicture.setEnabled(true);
                                    Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show();
                                    if (getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).refreshProfileIcon();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    btnChangeProfilePicture.setEnabled(true);
                                    Toast.makeText(getContext(), "Saved to auth only", Toast.LENGTH_SHORT).show();
                                }))
                .addOnFailureListener(e -> {
                    btnChangeProfilePicture.setEnabled(true);
                    Toast.makeText(getContext(), "Failed to update profile picture", Toast.LENGTH_SHORT).show();
                });
    }

    //downloads the profile image on a background thread and shows it in the profile page
    private void loadProfilePicture(String profilePictureUrl) {
        if (TextUtils.isEmpty(profilePictureUrl)) {
            Uri authPhoto = currentUser.getPhotoUrl();
            profilePictureUrl = authPhoto != null ? authPhoto.toString() : null;
        }

        if (TextUtils.isEmpty(profilePictureUrl)) {
            return;
        }

        String finalProfilePictureUrl = profilePictureUrl;
        imageExecutor.execute(() -> {
            try (InputStream input = new URL(finalProfilePictureUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        ivProfilePicture.setPadding(0, 0, 0, 0);
                        ivProfilePicture.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    //shows a password change dialog and re authenticates before updating firebase auth
    private void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null, false);

        TextInputEditText etCurrentPassword = dialogView.findViewById(R.id.et_current_password);
        TextInputEditText etNewPassword = dialogView.findViewById(R.id.et_new_password);
        TextInputEditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_new_password);
        TextInputLayout currentLayout = dialogView.findViewById(R.id.til_current_password);
        TextInputLayout newLayout = dialogView.findViewById(R.id.til_new_password);
        TextInputLayout confirmLayout = dialogView.findViewById(R.id.til_confirm_new_password);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Change password")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String currentPassword = etCurrentPassword.getText() != null
                    ? etCurrentPassword.getText().toString()
                    : "";
            String newPassword = etNewPassword.getText() != null
                    ? etNewPassword.getText().toString()
                    : "";
            String confirmPassword = etConfirmPassword.getText() != null
                    ? etConfirmPassword.getText().toString()
                    : "";

            currentLayout.setError(null);
            newLayout.setError(null);
            confirmLayout.setError(null);

            if (TextUtils.isEmpty(currentPassword)) {
                currentLayout.setError("Current password is required");
                return;
            }
            if (!isStrongPassword(newPassword)) {
                newLayout.setError("Use 8+ chars with uppercase, lowercase and a number");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                confirmLayout.setError("Passwords do not match");
                return;
            }

            updatePassword(currentPassword, newPassword, dialog);
        }));

        dialog.show();
    }

    private boolean isStrongPassword(String password) {
        return password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*");
    }

    private void updatePassword(String currentPassword, String newPassword, AlertDialog dialog) {
        if (currentUser == null || currentUser.getEmail() == null) {
            Toast.makeText(getContext(), "Could not update password", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUser.reauthenticate(
                        EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword))
                .addOnSuccessListener(aVoid ->
                        currentUser.updatePassword(newPassword)
                                .addOnSuccessListener(updated -> {
                                    dialog.dismiss();
                                    Toast.makeText(getContext(), "Password updated", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Failed to update password: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Current password is incorrect", Toast.LENGTH_LONG).show());
    }

    //removes active Firebase listeners when the fragment view is destroyed
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (rootRef != null && currentUser != null) {
            if (userListener != null) {
                rootRef.child("users").child(currentUser.getUid()).removeEventListener(userListener);
            }
            if (friendsListener != null) {
                rootRef.child("friends").child(currentUser.getUid()).removeEventListener(friendsListener);
            }
        }
    }
}
