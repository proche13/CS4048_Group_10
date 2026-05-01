package com.example.zerovelocity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private DatabaseReference rootRef;
    private FirebaseUser currentUser;

    private EditText etDisplayName;
    private ImageView ivProfilePicture;
    private TextView tvEmail;
    private TextView tvFriendsCount;
    private TextView tvTotals;
    private TextView tvFriendsPreview;
    private Button btnDeleteAccount;
    private Uri cameraProfilePictureUri;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> profileGalleryLauncher;
    private ActivityResultLauncher<Uri> profileCameraLauncher;
    private ActivityResultLauncher<String> profileCameraPermissionLauncher;

    private ValueEventListener userListener;
    private ValueEventListener friendsListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Opens the gallery and uploads the chosen image as the user's profile picture.
        profileGalleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadProfilePicture(uri);
                    }
                });

        // Stores a camera capture in the Uri created just before launching the camera.
        profileCameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraProfilePictureUri != null) {
                        uploadProfilePicture(cameraProfilePictureUri);
                    }
                });

        // Requests camera permission only when the user chooses the camera option.
        profileCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openProfileCamera();
                    } else {
                        Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
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

        rootRef = FirebaseRefs.root();

        etDisplayName = view.findViewById(R.id.et_profile_display_name);
        ivProfilePicture = view.findViewById(R.id.iv_profile_picture);
        tvEmail = view.findViewById(R.id.tv_profile_email);
        tvFriendsCount = view.findViewById(R.id.tv_profile_friends_count);
        tvTotals = view.findViewById(R.id.tv_profile_totals);
        tvFriendsPreview = view.findViewById(R.id.tv_profile_friends_preview);
        Button btnSave = view.findViewById(R.id.btn_save_profile);
        Button btnChangeProfilePicture = view.findViewById(R.id.btn_change_profile_picture);
        Button btnLogout = view.findViewById(R.id.btn_logout);
        btnDeleteAccount = view.findViewById(R.id.btn_delete_account);

        tvEmail.setText(currentUser.getEmail());
        btnSave.setOnClickListener(v -> saveProfile());
        btnChangeProfilePicture.setOnClickListener(v -> showProfilePictureOptions());
        btnLogout.setOnClickListener(v -> logout());
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

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
                if (!TextUtils.isEmpty(profilePictureUrl)) {
                    loadProfilePicture(profilePictureUrl);
                }
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

    //builds a short stacked preview of names for display on the profile page
    private String buildPreviewText(List<String> names, String emptyText) {
        if (names.isEmpty()) {
            return emptyText;
        }

        int limit = Math.min(names.size(), 5);
        return TextUtils.join("\n", names.subList(0, limit));
    }

    //saves an updated display name to both Firebase Auth and the users table
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
                        saveDisplayNameToDatabase(updatedName)
                                .addOnSuccessListener(value -> {
                                    Toast.makeText(getContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Saved to auth only", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show());
    }

    //keeps both the visible name and lowercase search key in sync for friend search
    private com.google.android.gms.tasks.Task<Void> saveDisplayNameToDatabase(String updatedName) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", updatedName);
        updates.put("displayNameLowercase", updatedName.toLowerCase(Locale.getDefault()));
        return rootRef.child("users").child(currentUser.getUid()).updateChildren(updates);
    }

    //lets the user choose between camera and gallery when replacing their profile picture
    private void showProfilePictureOptions() {
        String[] options = {"Take a photo", "Choose from gallery"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Change profile picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCameraThenOpen();
                    } else {
                        profileGalleryLauncher.launch("image/*");
                    }
                })
                .show();
    }

    //checks camera permission before opening the device camera
    private void requestCameraThenOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openProfileCamera();
        } else {
            profileCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    //creates a private cache file and gives the camera permission to write into it
    private void openProfileCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "images");
            dir.mkdirs();
            File tmp = File.createTempFile("profile_", ".jpg", dir);
            cameraProfilePictureUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    tmp);
            profileCameraLauncher.launch(cameraProfilePictureUri);
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Could not open camera", Toast.LENGTH_SHORT).show();
        }
    }

    //uploads the image, stores its download URL, and refreshes both profile screen and toolbar icon
    private void uploadProfilePicture(Uri imageUri) {
        if (currentUser == null) {
            return;
        }

        ivProfilePicture.setImageURI(imageUri);
        StorageReference profileImageRef = FirebaseStorage.getInstance()
                .getReference("profilePictures/" + currentUser.getUid() + "/profile.jpg");

        profileImageRef.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return profileImageRef.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                            .setPhotoUri(downloadUri)
                            .build();
                    currentUser.updateProfile(request)
                            .addOnCompleteListener(profileTask ->
                                    rootRef.child("users")
                                            .child(currentUser.getUid())
                                            .child("profilePictureUrl")
                                            .setValue(downloadUri.toString())
                                            .addOnSuccessListener(unused -> {
                                                Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show();
                                                if (getActivity() instanceof MainActivity) {
                                                    ((MainActivity) getActivity()).refreshProfileIcon(downloadUri.toString());
                                                }
                                            })
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(getContext(), "Image uploaded but profile was not saved",
                                                            Toast.LENGTH_LONG).show()));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Could not upload profile picture: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    //downloads the existing profile photo without blocking the UI thread
    private void loadProfilePicture(String profilePictureUrl) {
        imageExecutor.execute(() -> {
            try (InputStream input = new URL(profilePictureUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null && isAdded()) {
                    requireActivity().runOnUiThread(() -> ivProfilePicture.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
            }
        });
    }

    //signs out without deleting anything and returns to the login screen
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        returnToLogin();
    }

    //shows a password confirmation dialog before starting the destructive delete flow
    private void showDeleteAccountDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_delete_account, null, false);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.til_delete_password);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.et_delete_password);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Delete account")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", null)
                .create();

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String password = passwordInput.getText() != null
                            ? passwordInput.getText().toString()
                            : "";

                    if (TextUtils.isEmpty(password)) {
                        passwordLayout.setError("Password is required");
                        return;
                    }

                    passwordLayout.setError(null);
                    confirmDeleteAccount(password, dialog);
                }));

        dialog.show();
    }

    //reauthenticates with the typed password so Firebase Auth allows account deletion
    private void confirmDeleteAccount(String password, AlertDialog dialog) {
        if (currentUser == null || TextUtils.isEmpty(currentUser.getEmail())) {
            Toast.makeText(getContext(), "Unable to confirm this account", Toast.LENGTH_LONG).show();
            return;
        }

        setDeleteInProgress(true);

        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> deleteDatabaseData(dialog))
                .addOnFailureListener(e -> {
                    setDeleteInProgress(false);
                    Toast.makeText(getContext(), "Password confirmation failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    //builds one atomic database update so partial profile/log deletion is avoided
    private void deleteDatabaseData(AlertDialog dialog) {
        String uid = currentUser.getUid();

        rootRef.child("consumptionLogs")
                .orderByChild("userID")
                .equalTo(uid)
                .get()
                .addOnSuccessListener(logSnapshot ->
                        rootRef.child("friends").child(uid).get()
                                .addOnSuccessListener(friendsSnapshot ->
                                        rootRef.child("friendRequests").child(uid).get()
                                                .addOnSuccessListener(requestsSnapshot ->
                                                        rootRef.child("sentRequests").child(uid).get()
                                                                .addOnSuccessListener(sentRequestsSnapshot ->
                                                                        rootRef.child("Events").get()
                                                                                .addOnSuccessListener(eventsSnapshot -> {
                                                                                    Map<String, Object> updates = new HashMap<>();
                                                                                    updates.put("users/" + uid, null);

                                                                                    for (DataSnapshot log : logSnapshot.getChildren()) {
                                                                                        updates.put("consumptionLogs/" + log.getKey(), null);
                                                                                    }

                                                                                    for (DataSnapshot friend : friendsSnapshot.getChildren()) {
                                                                                        String friendUid = friend.getKey();
                                                                                        if (!TextUtils.isEmpty(friendUid)) {
                                                                                            // Delete both sides one child at a time so the write matches the rules.
                                                                                            updates.put("friends/" + uid + "/" + friendUid, null);
                                                                                            updates.put("friends/" + friendUid + "/" + uid, null);
                                                                                        }
                                                                                    }

                                                                                    for (DataSnapshot request : requestsSnapshot.getChildren()) {
                                                                                        String senderUid = request.getKey();
                                                                                        if (!TextUtils.isEmpty(senderUid)) {
                                                                                            //incoming requests have a matching sentRequests marker under the sender
                                                                                            updates.put("friendRequests/" + uid + "/" + senderUid, null);
                                                                                            updates.put("sentRequests/" + senderUid + "/" + uid, null);
                                                                                        }
                                                                                    }

                                                                                    for (DataSnapshot sentRequest : sentRequestsSnapshot.getChildren()) {
                                                                                        String targetUid = sentRequest.getKey();
                                                                                        if (!TextUtils.isEmpty(targetUid)) {
                                                                                            //outgoing requests are stored under the target users friendRequests node
                                                                                            updates.put("sentRequests/" + uid + "/" + targetUid, null);
                                                                                            updates.put("friendRequests/" + targetUid + "/" + uid, null);
                                                                                        }
                                                                                    }

                                                                                    for (DataSnapshot event : eventsSnapshot.getChildren()) {
                                                                                        String eventId = event.getKey();
                                                                                        String createdBy = event.child("createdBy").getValue(String.class);
                                                                                        if (TextUtils.isEmpty(eventId)) {
                                                                                            continue;
                                                                                        }
                                                                                        if (uid.equals(createdBy)) {
                                                                                            updates.put("Events/" + eventId, null);
                                                                                        } else if (event.child("invites").child(uid).exists()) {
                                                                                            updates.put("Events/" + eventId + "/invites/" + uid, null);
                                                                                        }
                                                                                    }

                                                                                    rootRef.updateChildren(updates)
                                                                                            .addOnSuccessListener(unused ->
                                                                                                    deleteStorageDataThenAuthAccount(dialog))
                                                                                            .addOnFailureListener(e -> {
                                                                                                setDeleteInProgress(false);
                                                                                                Toast.makeText(getContext(),
                                                                                                        "Could not delete account data: " + e.getMessage(),
                                                                                                        Toast.LENGTH_LONG).show();
                                                                                            });
                                                                                })
                                                                                .addOnFailureListener(e -> handleDeleteReadFailure(e)))
                                                                .addOnFailureListener(e -> handleDeleteReadFailure(e)))
                                                .addOnFailureListener(e -> handleDeleteReadFailure(e)))
                                .addOnFailureListener(e -> handleDeleteReadFailure(e)))
                .addOnFailureListener(e -> handleDeleteReadFailure(e));
    }

    //storage cleanup is best effort. the database has already been removed before Auth is deleted
    private void deleteStorageDataThenAuthAccount(AlertDialog dialog) {
        String uid = currentUser.getUid();
        StorageReference storageRoot = FirebaseStorage.getInstance().getReference();

        deleteStorageFolder(storageRoot.child("logs").child(uid), () ->
                storageRoot.child("profilePictures").child(uid).delete()
                        .addOnCompleteListener(unused ->
                                deleteStorageFolder(storageRoot.child("profilePictures").child(uid),
                                        () -> deleteAuthAccount(dialog))));
    }

    //deletes every file under a storage folder, then calls the continuation even if listing fails
    private void deleteStorageFolder(StorageReference folderRef, Runnable onComplete) {
        folderRef.listAll()
                .addOnSuccessListener(result -> {
                    int totalItems = result.getItems().size();
                    if (totalItems == 0) {
                        onComplete.run();
                        return;
                    }

                    AtomicInteger remaining = new AtomicInteger(totalItems);
                    for (StorageReference item : result.getItems()) {
                        item.delete().addOnCompleteListener(task -> {
                            if (remaining.decrementAndGet() == 0) {
                                onComplete.run();
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> onComplete.run());
    }

    //removes the Firebase Auth user only after owned app data has been cleaned up
    private void deleteAuthAccount(AlertDialog dialog) {
        currentUser.delete()
                .addOnSuccessListener(unused -> {
                    dialog.dismiss();
                    Toast.makeText(getContext(), "Account deleted", Toast.LENGTH_LONG).show();
                    returnToLogin();
                })
                .addOnFailureListener(e -> {
                    setDeleteInProgress(false);
                    Toast.makeText(getContext(), "Could not delete account: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void handleDeleteReadFailure(Exception e) {
        setDeleteInProgress(false);
        Toast.makeText(getContext(), "Could not check account data: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }

    private void setDeleteInProgress(boolean inProgress) {
        if (btnDeleteAccount != null) {
            btnDeleteAccount.setEnabled(!inProgress);
        }
    }

    private void returnToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
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

        imageExecutor.shutdownNow();
    }
}
