package com.example.zerovelocity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
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

public class FeedFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_START_IN_MAP_MODE = "start_in_map_mode";

    public static FeedFragment newInstance(boolean startInMapMode) {
        FeedFragment fragment = new FeedFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_START_IN_MAP_MODE, startInMapMode);
        fragment.setArguments(args);
        return fragment;
    }

    private RecyclerView rvFeed;
    private View mapContainer;
    private TextView tvLeader;
    private MaterialButton btnMapToggle;
    private FeedAdapter adapter;

    private DatabaseReference rootRef;
    private String myUid;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean mapMode;
    private boolean mapFragmentCreated;
    private boolean hasCenteredMap;
    private ActivityResultLauncher<String[]> mapLocationPermissionLauncher;

    private ValueEventListener friendsListener;
    private ValueEventListener logsListener;
    private ValueEventListener usersListener;

    private final HashSet<String> friendIds = new HashSet<>();
    private final List<LogItem> latestLogs = new ArrayList<>();
    private final Map<String, String> profilePictureUrlsByUserId = new HashMap<>();

    public FeedFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The map can work without location permission but we need the permission to lets us zoom
        // straight to the current user and show the blue current-location dot
        mapLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean granted = Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) {
                        enableMyLocationAndCenter();
                    } else {
                        Toast.makeText(requireContext(), "Map location permission denied", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_feed, container, false);

        rvFeed = view.findViewById(R.id.rv_feed);
        mapContainer = view.findViewById(R.id.map_container);
        tvLeader = view.findViewById(R.id.tv_leader);
        btnMapToggle = view.findViewById(R.id.btn_feed_map_toggle);

        rvFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FeedAdapter(new ArrayList<>());
        rvFeed.setAdapter(adapter);
        btnMapToggle.setOnClickListener(v -> toggleMapMode());

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Not logged in", Toast.LENGTH_SHORT).show();
            return view;
        }

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        rootRef = FirebaseRefs.root();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        listenToUserProfiles();
        loadFriendsAndThenLogs();

        Bundle args = getArguments();
        if (args != null && args.getBoolean(ARG_START_IN_MAP_MODE, false)) {
            toggleMapMode();
        }

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

                            logs.add(new LogItem(userId, username, category, itemName, locationLabel, description,
                                    units, latitude, longitude, imageUrl, timestamp != null ? timestamp : 0L));
                        }

                        Collections.sort(logs, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                        latestLogs.clear();
                        latestLogs.addAll(logs);

                        renderFeedList();
                        renderMapMarkers();
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

        for (LogItem log : latestLogs) {
            totalsByUserId.put(log.userId, totalsByUserId.getOrDefault(log.userId, 0f) + log.units);
            usernamesByUserId.put(log.userId, log.username);
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
                    displayName + " logged " + log.units + " " + categoryText,
                    itemText + " at " + locationText,
                    log.description,
                    rank,
                    profilePictureUrlsByUserId.get(log.userId),
                    log.imageUrl));
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

    private void toggleMapMode() {
        mapMode = !mapMode;
        rvFeed.setVisibility(mapMode ? View.GONE : View.VISIBLE);
        mapContainer.setVisibility(mapMode ? View.VISIBLE : View.GONE);
        btnMapToggle.setText(mapMode ? "Feed" : "Map");

        if (mapMode && !mapFragmentCreated) {
            mapFragmentCreated = true;
            SupportMapFragment mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.map_container, mapFragment)
                    .commit();
            mapFragment.getMapAsync(this);
        } else if (mapMode) {
            requestMapLocationIfNeeded();
            renderMapMarkers();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        requestMapLocationIfNeeded();
        enableMyLocationAndCenter();
        renderMapMarkers();
    }

    private void requestMapLocationIfNeeded() {
        if (!hasLocationPermission()) {
            mapLocationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocationAndCenter() {
        if (googleMap == null || !hasLocationPermission()) {
            return;
        }

        googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);

        if (!hasCenteredMap) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(this::centerOnUserLocation)
                    .addOnFailureListener(e -> fusedLocationClient.getLastLocation()
                            .addOnSuccessListener(this::centerOnUserLocation));
        }
    }

    private void centerOnUserLocation(Location location) {
        if (googleMap == null || location == null || hasCenteredMap) {
            return;
        }

        hasCenteredMap = true;
        LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 15f));
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Plots all friend/self feed logs that have coordinates saved from the log screen.
    private void renderMapMarkers() {
        if (googleMap == null) {
            return;
        }

        googleMap.clear();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        Map<String, Integer> coordinateCounts = new HashMap<>();
        int markerCount = 0;
        LatLng firstPosition = null;

        for (LogItem log : latestLogs) {
            if (log.latitude == null || log.longitude == null) {
                continue;
            }

            LatLng position = getOffsetPosition(log, coordinateCounts);
            if (firstPosition == null) {
                firstPosition = position;
            }
            String displayName = TextUtils.equals(log.userId, myUid) ? "You" : log.username;
            String categoryText = log.category.toLowerCase();
            String itemText = TextUtils.isEmpty(log.itemName) ? categoryText : log.itemName;
            String locationText = TextUtils.isEmpty(log.locationLabel) ? "Logged from here" : log.locationLabel;
            boolean isMyLog = TextUtils.equals(log.userId, myUid);
            boolean isDrink = TextUtils.equals(log.category, LogEntry.Category.Drink.name());
            float hue = getMarkerHue(isMyLog, isDrink);

            googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(displayName + " logged " + log.units + " " + categoryText)
                    .snippet(itemText + " - " + locationText)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));

            boundsBuilder.include(position);
            markerCount++;
        }

        if (markerCount == 1) {
            hasCenteredMap = true;
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPosition, 14f));
        } else if (markerCount > 1) {
            hasCenteredMap = true;
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120));
        } else {
            enableMyLocationAndCenter();
        }
    }
    //sets color for the marker based on the type and if you or a friend has made the log
    private float getMarkerHue(boolean isMyLog, boolean isDrink) {
        if (isMyLog) {
            return isDrink ? BitmapDescriptorFactory.HUE_ORANGE : BitmapDescriptorFactory.HUE_RED;
        }
        return isDrink ? BitmapDescriptorFactory.HUE_GREEN : BitmapDescriptorFactory.HUE_BLUE;
    }

    //separates markers that have exactly the same coordinates so none are hidden behind another
    private LatLng getOffsetPosition(LogItem log, Map<String, Integer> coordinateCounts) {
        String key = String.format("%.6f,%.6f", log.latitude, log.longitude);
        int index = coordinateCounts.getOrDefault(key, 0);
        coordinateCounts.put(key, index + 1);

        if (index == 0) {
            return new LatLng(log.latitude, log.longitude);
        }

        int seed = Math.abs((log.userId + log.username + log.itemName + log.timestamp).hashCode());
        double angle = (seed % 360) * Math.PI / 180.0;
        double radius = 0.00004 + ((seed % 50) / 1000000.0);
        double offsetLat = Math.cos(angle) * radius;
        double offsetLng = Math.sin(angle) * radius;
        return new LatLng(log.latitude + offsetLat, log.longitude + offsetLng);
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

        LogItem(String userId, String username, String category, String itemName, String locationLabel,
                String description,
                float units, Double latitude, Double longitude, String imageUrl, long timestamp) {
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
        }
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
        googleMap = null;
    }
}
