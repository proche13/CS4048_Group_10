package com.example.zerovelocity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private DatabaseReference rootRef;
    private FirebaseUser currentUser;

    private EditText etDisplayName;
    private TextView tvEmail;
    private TextView tvFriendsCount;
    private TextView tvTotals;
    private TextView tvFriendsPreview;

    private ValueEventListener userListener;
    private ValueEventListener friendsListener;

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
        tvEmail = view.findViewById(R.id.tv_profile_email);
        tvFriendsCount = view.findViewById(R.id.tv_profile_friends_count);
        tvTotals = view.findViewById(R.id.tv_profile_totals);
        tvFriendsPreview = view.findViewById(R.id.tv_profile_friends_preview);
        Button btnSave = view.findViewById(R.id.btn_save_profile);

        tvEmail.setText(currentUser.getEmail());
        btnSave.setOnClickListener(v -> saveProfile());

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
                Double totalDrinks = snapshot.child("totalDrinks").getValue(Double.class);
                Double totalCigarettes = snapshot.child("totalCigarettes").getValue(Double.class);

                if (!etDisplayName.hasFocus()) {
                    etDisplayName.setText(displayName != null ? displayName : "");
                }

                tvEmail.setText(email != null ? email : currentUser.getEmail());
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
                        rootRef.child("users").child(currentUser.getUid()).child("displayName").setValue(updatedName)
                                .addOnSuccessListener(value -> {
                                    Toast.makeText(getContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Saved to auth only", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show());
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
