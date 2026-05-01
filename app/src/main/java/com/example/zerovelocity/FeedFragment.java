package com.example.zerovelocity;

import android.content.Context;
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
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class FeedFragment extends Fragment implements FeedAdapter.OnInteractionListener {

    private static final String FEED_PREFS = "feed_preferences";
    private static final String KEY_LAST_SEEN_PREFIX = "last_seen_feed_";

    private RecyclerView rvFeed;
    private TextView tvLeader;
    private TextView tvFeedBadge;
    private FeedAdapter adapter;

    private DatabaseReference rootRef;
    private String myUid;
    private String myDisplayName = "";

    private ValueEventListener friendsListener;
    private ValueEventListener logsListener;
    private ValueEventListener usersListener;

    private final HashSet<String> friendIds = new HashSet<>();
    private final List<LogItem> latestLogs = new ArrayList<>();
    private final Map<String, String> profilePictureUrlsByUserId = new HashMap<>();

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
        tvFeedBadge = view.findViewById(R.id.tv_feed_badge);

        rvFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FeedAdapter(new ArrayList<>(), this);
        rvFeed.setAdapter(adapter);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Not logged in", Toast.LENGTH_SHORT).show();
            return view;
        }

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        rootRef = FirebaseRefs.root();

        listenToUserProfiles();
        loadFriendsAndThenLogs();
        loadMyDisplayName();

        return view;
    }

    private void listenToUserProfiles() {
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                profilePictureUrlsByUserId.clear();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String profilePictureUrl = userSnapshot.child("profilePictureUrl").getValue(String.class);
                    if (!TextUtils.isEmpty(profilePictureUrl)) {
                        profilePictureUrlsByUserId.put(userSnapshot.getKey(), profilePictureUrl);
                    }
                }

                renderFeedList();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        rootRef.child("users").addValueEventListener(usersListener);
    }

    private void loadFriendsAndThenLogs() {
        friendsListener = new ValueEventListener() {
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
                };
        rootRef.child("friends").child(myUid).addValueEventListener(friendsListener);
    }

    private void listenToLogs() {
        if (logsListener != null) {
            return;
        }

        logsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        List<LogItem> logs = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String userId = child.child("userID").getValue(String.class);
                            String username = child.child("username").getValue(String.class);
                            String category = child.child("category").getValue(String.class);
                            String itemName = child.child("itemName").getValue(String.class);
                            String locationLabel = child.child("location").getValue(String.class);
                            String description = child.child("description").getValue(String.class);
                            Double unitsValue = child.child("units").getValue(Double.class);
                            Double latitude = child.child("latitude").getValue(Double.class);
                            Double longitude = child.child("longitude").getValue(Double.class);
                            String imageUrl = child.child("imageUrl").getValue(String.class);
                            Long timestamp = child.child("timestamp").getValue(Long.class);

                            if (userId == null || !friendIds.contains(userId)) {
                                continue;
                            }

                            if (username == null || category == null) {
                                continue;
                            }

                            float units = unitsValue != null ? unitsValue.floatValue() : 0f;

                            // Read cheers
                            int cheerCount = (int) child.child("cheers").getChildrenCount();
                            boolean cheeredByMe = child.child("cheers").child(myUid).exists();

                            // Read emoji reactions
                            Map<String, Integer> reactionCounts = new HashMap<>();
                            String myReaction = null;
                            for (DataSnapshot r : child.child("reactions").getChildren()) {
                                String emoji = r.getValue(String.class);
                                if (emoji != null) {
                                    reactionCounts.merge(emoji, 1, Integer::sum);
                                    if (myUid.equals(r.getKey())) myReaction = emoji;
                                }
                            }

                            // Read comments
                            List<String[]> comments = new ArrayList<>();
                            for (DataSnapshot c : child.child("comments").getChildren()) {
                                String commenter = c.child("username").getValue(String.class);
                                String commentText = c.child("text").getValue(String.class);
                                if (commenter != null && commentText != null) {
                                    comments.add(new String[]{commenter, commentText});
                                }
                            }

                            logs.add(new LogItem(child.getKey(), userId, username, category,
                                    itemName, locationLabel, description, units,
                                    latitude, longitude, imageUrl, timestamp != null ? timestamp : 0L,
                                    cheerCount, cheeredByMe, reactionCounts, myReaction,
                                    comments, comments.size()));
                        }

                        Collections.sort(logs, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                        latestLogs.clear();
                        latestLogs.addAll(logs);

                        renderFeedList();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                };
        rootRef.child("consumptionLogs").addValueEventListener(logsListener);
    }

    private void renderFeedList() {
        if (adapter == null || tvLeader == null) {
            return;
        }

        Map<String, Float> totalsByUserId = new HashMap<>();
        Map<String, String> usernamesByUserId = new HashMap<>();
        long latestFriendPostTimestamp = 0L;
        int unseenFriendPostCount = 0;
        long lastSeenFeedTimestamp = getLastSeenFeedTimestamp();

        for (LogItem log : latestLogs) {
            totalsByUserId.put(log.userId, totalsByUserId.getOrDefault(log.userId, 0f) + log.units);
            usernamesByUserId.put(log.userId, log.username);
            if (!TextUtils.equals(log.userId, myUid)) {
                latestFriendPostTimestamp = Math.max(latestFriendPostTimestamp, log.timestamp);
                if (log.timestamp > lastSeenFeedTimestamp) {
                    unseenFriendPostCount++;
                }
            }
        }

        Map<String, Integer> ranksByUserId = getTopThreeRanks(totalsByUserId);

        List<FeedAdapter.FeedItem> formattedFeed = new ArrayList<>();
        for (LogItem log : latestLogs) {
            String displayName = TextUtils.equals(log.userId, myUid) ? "You" : log.username;
            int rank = ranksByUserId.containsKey(log.userId) ? ranksByUserId.get(log.userId) : 0;
            String categoryText = log.category.toLowerCase();
            String itemText = TextUtils.isEmpty(log.itemName) ? categoryText : log.itemName;
            String locationText = TextUtils.isEmpty(log.locationLabel) ? "Unknown spot" : log.locationLabel;
            formattedFeed.add(new FeedAdapter.FeedItem(
                    log.postId,
                    displayName + " logged " + log.units + " " + categoryText,
                    itemText + " at " + locationText,
                    log.description,
                    rank,
                    profilePictureUrlsByUserId.get(log.userId),
                    log.imageUrl,
                    log.cheerCount,
                    log.cheeredByMe,
                    log.reactionCounts,
                    log.myReaction,
                    log.comments,
                    log.totalCommentCount));
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

        updateFeedBadge(unseenFriendPostCount);
        if (latestFriendPostTimestamp > 0L) {
            saveLastSeenFeedTimestamp(latestFriendPostTimestamp);
        }
    }

    private void updateFeedBadge(int unseenFriendPostCount) {
        if (tvFeedBadge == null) {
            return;
        }

        if (unseenFriendPostCount <= 0) {
            tvFeedBadge.setVisibility(View.GONE);
            return;
        }

        tvFeedBadge.setVisibility(View.VISIBLE);
        tvFeedBadge.setText(unseenFriendPostCount > 99 ? "99+" : String.valueOf(unseenFriendPostCount));
    }

    private long getLastSeenFeedTimestamp() {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(myUid)) {
            return 0L;
        }

        return context.getSharedPreferences(FEED_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SEEN_PREFIX + myUid, 0L);
    }

    private void saveLastSeenFeedTimestamp(long timestamp) {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(myUid)) {
            return;
        }

        context.getSharedPreferences(FEED_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SEEN_PREFIX + myUid, timestamp)
                .apply();
    }

    private Map<String, Integer> getTopThreeRanks(Map<String, Float> totalsByUserId) {
        List<Map.Entry<String, Float>> rankedUsers = new ArrayList<>(totalsByUserId.entrySet());
        Collections.sort(rankedUsers, (a, b) -> Float.compare(b.getValue(), a.getValue()));

        Map<String, Integer> ranksByUserId = new HashMap<>();
        for (int i = 0; i < rankedUsers.size() && i < 3; i++) {
            ranksByUserId.put(rankedUsers.get(i).getKey(), i + 1);
        }
        return ranksByUserId;
    }

    static class LogItem {
        String postId;
        String userId;
        String username;
        String category;
        String itemName;
        String locationLabel;
        String description;
        float units;
        Double latitude;
        Double longitude;
        String imageUrl;
        long timestamp;
        int cheerCount;
        boolean cheeredByMe;
        Map<String, Integer> reactionCounts;
        String myReaction;
        List<String[]> comments;
        int totalCommentCount;

        LogItem(String postId, String userId, String username, String category, String itemName,
                String locationLabel, String description, float units,
                Double latitude, Double longitude, String imageUrl, long timestamp,
                int cheerCount, boolean cheeredByMe, Map<String, Integer> reactionCounts,
                String myReaction, List<String[]> comments, int totalCommentCount) {
            this.postId = postId;
            this.userId = userId;
            this.username = username;
            this.category = category;
            this.itemName = itemName;
            this.locationLabel = locationLabel;
            this.description = description;
            this.units = units;
            this.latitude = latitude;
            this.longitude = longitude;
            this.imageUrl = imageUrl;
            this.timestamp = timestamp;
            this.cheerCount = cheerCount;
            this.cheeredByMe = cheeredByMe;
            this.reactionCounts = reactionCounts;
            this.myReaction = myReaction;
            this.comments = comments;
            this.totalCommentCount = totalCommentCount;
        }
    }

    private void loadMyDisplayName() {
        rootRef.child("users").child(myUid).child("displayName").get()
                .addOnSuccessListener(snapshot -> {
                    String name = snapshot.getValue(String.class);
                    if (!TextUtils.isEmpty(name)) myDisplayName = name;
                });
    }

    @Override
    public void onCheerToggle(String postId, boolean currentlyCheered) {
        DatabaseReference cheerRef = rootRef.child("consumptionLogs")
                .child(postId).child("cheers").child(myUid);
        if (currentlyCheered) cheerRef.removeValue();
        else cheerRef.setValue(true);
    }

    @Override
    public void onEmojiReact(String postId, String emoji, String currentMyReaction) {
        DatabaseReference reactionRef = rootRef.child("consumptionLogs")
                .child(postId).child("reactions").child(myUid);
        if (emoji.equals(currentMyReaction)) reactionRef.removeValue();
        else reactionRef.setValue(emoji);
    }

    @Override
    public void onCommentSubmit(String postId, String text) {
        Map<String, Object> comment = new HashMap<>();
        comment.put("uid", myUid);
        comment.put("username", TextUtils.isEmpty(myDisplayName) ? "User" : myDisplayName);
        comment.put("text", text);
        comment.put("timestamp", System.currentTimeMillis());
        rootRef.child("consumptionLogs").child(postId).child("comments").push().setValue(comment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (rootRef != null && myUid != null && friendsListener != null) {
            rootRef.child("friends").child(myUid).removeEventListener(friendsListener);
        }
        if (rootRef != null && logsListener != null) {
            rootRef.child("consumptionLogs").removeEventListener(logsListener);
        }
        if (rootRef != null && usersListener != null) {
            rootRef.child("users").removeEventListener(usersListener);
        }
        if (adapter != null) {
            adapter.shutdown();
        }
    }
}
