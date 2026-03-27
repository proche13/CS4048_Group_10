package com.example.zerovelocity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class FeedFragment extends Fragment {

    private RecyclerView rvFeed;
    private TextView tvLeader;
    private FeedAdapter adapter;

    private DatabaseReference rootRef;
    private String myUid;


    private final HashSet<String> friendIds = new HashSet<>();

    public FeedFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_feed, container, false);

        rvFeed = view.findViewById(R.id.rv_feed);
        tvLeader = view.findViewById(R.id.tv_leader);

        rvFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FeedAdapter(new ArrayList<>());
        rvFeed.setAdapter(adapter);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Not logged in", Toast.LENGTH_SHORT).show();
            return view;
        }

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        rootRef = FirebaseDatabase.getInstance().getReference();

        loadFriendsAndThenLogs();

        return view;
    }

    private void loadFriendsAndThenLogs() {
        rootRef.child("friends").child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        friendIds.clear();
                        friendIds.add(myUid);

                        for (DataSnapshot child : snapshot.getChildren()) {
                            friendIds.add(child.getKey());
                        }

                        listenToLogs();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void listenToLogs() {
        rootRef.child("consumptionLogs")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        List<LogItem> logs = new ArrayList<>();
                        Map<String, Float> totalsByUserId = new HashMap<>();
                        Map<String, String> usernamesByUserId = new HashMap<>();

                        for (DataSnapshot child : snapshot.getChildren()) {
                            String userId = child.child("userID").getValue(String.class);
                            String username = child.child("username").getValue(String.class);
                            String category = child.child("category").getValue(String.class);
                            Double unitsValue = child.child("units").getValue(Double.class);
                            Long timestamp = child.child("timestamp").getValue(Long.class);

                            if (userId == null || !friendIds.contains(userId)) {
                                continue;
                            }

                            if (username == null || category == null) {
                                continue;
                            }

                            float units = unitsValue != null ? unitsValue.floatValue() : 0f;

                            logs.add(new LogItem(userId, username, category, units, timestamp != null ? timestamp : 0L));

                            usernamesByUserId.put(userId, username);
                            totalsByUserId.put(userId, totalsByUserId.getOrDefault(userId, 0f) + units);
                        }

                        Collections.sort(logs, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                        List<String> formattedFeed = new ArrayList<>();
                        for (LogItem log : logs) {
                            String displayName = TextUtils.equals(log.userId, myUid) ? "You" : log.username;
                            formattedFeed.add(displayName + " logged " + log.units + " " + log.category.toLowerCase());
                        }

                        adapter.update(formattedFeed);

                        String topUserId = null;
                        float maxUnits = -1f;

                        for (Map.Entry<String, Float> entry : totalsByUserId.entrySet()) {
                            if (entry.getValue() > maxUnits) {
                                maxUnits = entry.getValue();
                                topUserId = entry.getKey();
                            }
                        }

                        if (topUserId != null) {
                            String topUsername = TextUtils.equals(topUserId, myUid)
                                    ? "You"
                                    : usernamesByUserId.get(topUserId);

                            tvLeader.setText("Top user: " + topUsername);
                        } else {
                            tvLeader.setText("Top user: None");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    static class LogItem {
        String userId;
        String username;
        String category;
        float units;
        long timestamp;

        LogItem(String userId, String username, String category, float units, long timestamp) {
            this.userId = userId;
            this.username = username;
            this.category = category;
            this.units = units;
            this.timestamp = timestamp;
        }
    }
}