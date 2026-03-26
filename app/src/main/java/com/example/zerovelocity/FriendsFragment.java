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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Friends screen - search for and add/remove friends
public class FriendsFragment extends Fragment {

    private EditText etSearch;
    private TextView tvSearchHeader;
    private TextView tvFriendsHeader;

    private DatabaseReference dbRef;
    private String myUid;
    private final Set<String> friendUids = new HashSet<>();

    private UserAdapter searchAdapter;
    private UserAdapter friendsAdapter;
    private final List<UserItem> searchResults = new ArrayList<>();
    private final List<UserItem> friendsList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return view;
        myUid = currentUser.getUid();

        dbRef = FirebaseDatabase.getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference();

        etSearch = view.findViewById(R.id.et_search);
        Button btnSearch = view.findViewById(R.id.btn_search);
        tvSearchHeader = view.findViewById(R.id.tv_search_header);
        tvFriendsHeader = view.findViewById(R.id.tv_friends_header);

        RecyclerView rvSearchResults = view.findViewById(R.id.rv_search_results);
        RecyclerView rvFriends = view.findViewById(R.id.rv_friends);

        searchAdapter = new UserAdapter(searchResults, "Add", this::addFriend);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSearchResults.setAdapter(searchAdapter);

        friendsAdapter = new UserAdapter(friendsList, "Remove", this::removeFriend);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFriends.setAdapter(friendsAdapter);

        btnSearch.setOnClickListener(v -> searchUsers());

        loadFriends();

        return view;
    }

    private void loadFriends() {
        dbRef.child("friends").child(myUid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                friendsList.clear();
                friendUids.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.child("uid").getValue(String.class);
                    String displayName = child.child("displayName").getValue(String.class);
                    if (uid != null && displayName != null) {
                        friendsList.add(new UserItem(uid, displayName));
                        friendUids.add(uid);
                    }
                }
                friendsAdapter.notifyDataSetChanged();
                tvFriendsHeader.setVisibility(friendsList.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load friends", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchUsers() {
        String query = etSearch.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(getContext(), "Enter a name to search", Toast.LENGTH_SHORT).show();
            return;
        }

        // Query users whose displayName starts with the search term (case-sensitive)
        dbRef.child("users").orderByChild("displayName")
                .startAt(query).endAt(query + "\uf8ff")
                .get()
                .addOnSuccessListener(snapshot -> {
                    searchResults.clear();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String uid = child.child("uid").getValue(String.class);
                        String displayName = child.child("displayName").getValue(String.class);
                        // Exclude self and already-added friends
                        if (uid != null && !uid.equals(myUid) && !friendUids.contains(uid)) {
                            searchResults.add(new UserItem(uid, displayName));
                        }
                    }
                    searchAdapter.notifyDataSetChanged();
                    tvSearchHeader.setVisibility(searchResults.isEmpty() ? View.GONE : View.VISIBLE);
                    if (searchResults.isEmpty()) {
                        Toast.makeText(getContext(), "No users found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void addFriend(UserItem user) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.uid);
        data.put("displayName", user.displayName);

        dbRef.child("friends").child(myUid).child(user.uid).setValue(data)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), user.displayName + " added", Toast.LENGTH_SHORT).show();
                    // Remove from search results since they're now a friend
                    searchResults.removeIf(u -> u.uid.equals(user.uid));
                    searchAdapter.notifyDataSetChanged();
                    if (searchResults.isEmpty()) {
                        tvSearchHeader.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to add friend", Toast.LENGTH_SHORT).show());
    }

    private void removeFriend(UserItem user) {
        dbRef.child("friends").child(myUid).child(user.uid).removeValue()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(getContext(), user.displayName + " removed", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to remove friend", Toast.LENGTH_SHORT).show());
    }
}