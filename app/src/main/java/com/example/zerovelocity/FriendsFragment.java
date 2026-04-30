package com.example.zerovelocity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
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

// Friends screen: search users, send/accept/decline friend requests
public class FriendsFragment extends Fragment {

    private EditText etSearch;
    private TextView tvSearchHeader;
    private TextView tvFriendsHeader;
    private MaterialCardView cardSearchResults;
    private MaterialCardView cardFriends;
    private MaterialCardView cardRequests;

    private DatabaseReference dbRef;
    private String myUid;

    // uids already friends with the current user
    private final Set<String> friendUids = new HashSet<>();
    // uids the current user has already sent a request to (tracked locally per session)
    private final Set<String> sentRequestUids = new HashSet<>();

    private UserAdapter searchAdapter;
    private UserAdapter friendsAdapter;
    private RequestAdapter requestAdapter;

    private final List<UserItem> searchResults = new ArrayList<>();
    private final List<UserItem> friendsList = new ArrayList<>();
    private final List<UserItem> pendingRequests = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return view;
        myUid = currentUser.getUid();

        dbRef = FirebaseDatabase.getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference();

        etSearch = view.findViewById(R.id.et_search);
        MaterialButton btnSearch = view.findViewById(R.id.btn_search);
        tvSearchHeader = view.findViewById(R.id.tv_search_header);
        tvFriendsHeader = view.findViewById(R.id.tv_friends_header);
        cardSearchResults = view.findViewById(R.id.card_search_results);
        cardFriends = view.findViewById(R.id.card_friends);
        cardRequests = view.findViewById(R.id.card_requests);

        RecyclerView rvSearchResults = view.findViewById(R.id.rv_search_results);
        RecyclerView rvFriends = view.findViewById(R.id.rv_friends);
        RecyclerView rvRequests = view.findViewById(R.id.rv_requests);

        searchAdapter = new UserAdapter(searchResults, "Add", this::sendFriendRequest);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSearchResults.setAdapter(searchAdapter);

        friendsAdapter = new UserAdapter(friendsList, "Remove", this::removeFriend);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFriends.setAdapter(friendsAdapter);

        requestAdapter = new RequestAdapter(pendingRequests, new RequestAdapter.OnRequestAction() {
            @Override
            public void onAccept(UserItem user) { acceptRequest(user); }
            @Override
            public void onDecline(UserItem user) { declineRequest(user); }
        });
        rvRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRequests.setAdapter(requestAdapter);

        btnSearch.setOnClickListener(v -> searchUsers());

        loadFriends();
        loadIncomingRequests();

        return view;
    }

    // listens for changes to the current user's friend list and refreshes the recycler view
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
                boolean hasFriends = !friendsList.isEmpty();
                tvFriendsHeader.setVisibility(hasFriends ? View.VISIBLE : View.GONE);
                cardFriends.setVisibility(hasFriends ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load friends", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // listens for incoming friend requests sent to the current user
    private void loadIncomingRequests() {
        dbRef.child("friendRequests").child(myUid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                pendingRequests.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.child("uid").getValue(String.class);
                    String displayName = child.child("displayName").getValue(String.class);
                    if (uid != null && displayName != null) {
                        pendingRequests.add(new UserItem(uid, displayName));
                    }
                }
                requestAdapter.notifyDataSetChanged();
                cardRequests.setVisibility(pendingRequests.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load requests", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // searches the users node by display name, excluding self, existing friends, and already-requested users
    private void searchUsers() {
        String query = etSearch.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(getContext(), "Enter a name to search", Toast.LENGTH_SHORT).show();
            return;
        }

        dbRef.child("users").orderByChild("displayName")
                .startAt(query).endAt(query + "\uf8ff")
                .get()
                .addOnSuccessListener(snapshot -> {
                    searchResults.clear();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String uid = child.child("uid").getValue(String.class);
                        String displayName = child.child("displayName").getValue(String.class);
                        if (uid != null && !uid.equals(myUid)
                                && !friendUids.contains(uid)
                                && !sentRequestUids.contains(uid)) {
                            searchResults.add(new UserItem(uid, displayName));
                        }
                    }
                    searchAdapter.notifyDataSetChanged();
                    boolean hasResults = !searchResults.isEmpty();
                    tvSearchHeader.setVisibility(hasResults ? View.VISIBLE : View.GONE);
                    cardSearchResults.setVisibility(hasResults ? View.VISIBLE : View.GONE);
                    if (!hasResults) {
                        Toast.makeText(getContext(), "No users found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // sends a friend request by writing to friendRequests/{targetUid}/{myUid}
    private void sendFriendRequest(UserItem user) {
        dbRef.child("users").child(myUid).get().addOnSuccessListener(snapshot -> {
            String myDisplayName = snapshot.child("displayName").getValue(String.class);
            if (myDisplayName == null) myDisplayName = "Unknown";

            Map<String, Object> data = new HashMap<>();
            data.put("uid", myUid);
            data.put("displayName", myDisplayName);

            dbRef.child("friendRequests").child(user.uid).child(myUid).setValue(data)
                    .addOnSuccessListener(aVoid -> {
                        sentRequestUids.add(user.uid);
                        Toast.makeText(getContext(), "Request sent to " + user.displayName, Toast.LENGTH_SHORT).show();
                        searchResults.removeIf(u -> u.uid.equals(user.uid));
                        searchAdapter.notifyDataSetChanged();
                        if (searchResults.isEmpty()) {
                            tvSearchHeader.setVisibility(View.GONE);
                            cardSearchResults.setVisibility(View.GONE);
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed to send request: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    // accepts a friend request: writes friendship both ways, then removes the request
    private void acceptRequest(UserItem requester) {
        dbRef.child("users").child(myUid).get().addOnSuccessListener(snapshot -> {
            String myDisplayName = snapshot.child("displayName").getValue(String.class);
            if (myDisplayName == null) myDisplayName = "Unknown";

            Map<String, Object> meData = new HashMap<>();
            meData.put("uid", myUid);
            meData.put("displayName", myDisplayName);

            Map<String, Object> themData = new HashMap<>();
            themData.put("uid", requester.uid);
            themData.put("displayName", requester.displayName);

            // write friendship both ways
            dbRef.child("friends").child(myUid).child(requester.uid).setValue(themData);
            dbRef.child("friends").child(requester.uid).child(myUid).setValue(meData);

            // remove the request
            dbRef.child("friendRequests").child(myUid).child(requester.uid).removeValue()
                    .addOnSuccessListener(aVoid ->
                            Toast.makeText(getContext(), requester.displayName + " added as a friend", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed to accept request", Toast.LENGTH_SHORT).show());
        });
    }

    // declines a friend request by removing it from friendRequests
    private void declineRequest(UserItem requester) {
        dbRef.child("friendRequests").child(myUid).child(requester.uid).removeValue()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(getContext(), "Request declined", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to decline request", Toast.LENGTH_SHORT).show());
    }

    // removes a friend from both sides of the friends list
    private void removeFriend(UserItem user) {
        dbRef.child("friends").child(myUid).child(user.uid).removeValue();
        dbRef.child("friends").child(user.uid).child(myUid).removeValue()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(getContext(), user.displayName + " removed", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to remove friend", Toast.LENGTH_SHORT).show());
    }
}