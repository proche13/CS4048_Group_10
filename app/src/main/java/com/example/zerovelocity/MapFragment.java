package com.example.zerovelocity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.location.Address;
import android.location.Geocoder;
import android.widget.Button;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final double CLUSTER_STEP = 0.00035d;
    private static final float INDIVIDUAL_MARKER_ZOOM_THRESHOLD = 16f;
    private static final int DEFAULT_PIN_PHOTO_DIAMETER_DP = 50;
    private static final SimpleDateFormat EVENT_DATE_FORMAT =
            new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());

    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseReference rootRef;
    private String myUid;
    private boolean hasCenteredMap;
    private boolean waitingForInitialBounds;

    private TextView tvMapSubtitle;
    private ActivityResultLauncher<String[]> mapLocationPermissionLauncher;
    private ValueEventListener friendsListener;
    private ValueEventListener logsListener;
    private ValueEventListener usersListener;

    private final List<EventItem> myEvents = new ArrayList<>();
    private ValueEventListener eventsListener;
    private boolean isLocationPickMode;
    private View locationPickOverlay;

    private final HashSet<String> friendIds = new HashSet<>();
    private final List<MapLogItem> latestDrinkLogs = new ArrayList<>();
    private final Map<String, String> profilePictureUrlsByUserId = new HashMap<>();
    private final Map<String, Bitmap> rawProfileBitmapCache = new ConcurrentHashMap<>();
    private final HashSet<String> pendingProfileLoads = new HashSet<>();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mapLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean granted = Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) {
                        enableMyLocationAndCenter();
                    } else if (isAdded()) {
                        Toast.makeText(requireContext(), "Map location permission denied", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        tvMapSubtitle = view.findViewById(R.id.tv_map_subtitle);
        MaterialButton btnMapEvents = view.findViewById(R.id.btn_map_events);
        btnMapEvents.setOnClickListener(v -> showEventsDialog());

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Not logged in", Toast.LENGTH_SHORT).show();
            return view;
        }

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        rootRef = FirebaseRefs.root();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        locationPickOverlay = view.findViewById(R.id.location_pick_overlay);
        view.findViewById(R.id.btn_cancel_pick).setOnClickListener(v -> {
            isLocationPickMode = false;
            locationPickOverlay.setVisibility(View.GONE);
            view.findViewById(R.id.btn_map_events).setVisibility(View.VISIBLE);
            reopenCreateEvent();
        });
        view.findViewById(R.id.btn_confirm_pick).setOnClickListener(v -> {
            if (googleMap == null) return;
            isLocationPickMode = false;
            locationPickOverlay.setVisibility(View.GONE);
            view.findViewById(R.id.btn_map_events).setVisibility(View.VISIBLE);
            LatLng center = googleMap.getCameraPosition().target;
            CreateEventFragment.draftLat = center.latitude;
            CreateEventFragment.draftLng = center.longitude;
            CreateEventFragment.draftLabel = getLocationLabel(center.latitude, center.longitude);
            CreateEventFragment.hasDraft = true;
            reopenCreateEvent();
        });

        attachMapFragment();
        listenToUserProfiles();
        loadFriendsAndThenLogs();
        listenToEvents();

        return view;
    }

    private void attachMapFragment() {
        Fragment existingFragment = getChildFragmentManager().findFragmentById(R.id.map_fragment_container);
        if (existingFragment instanceof SupportMapFragment) {
            ((SupportMapFragment) existingFragment).getMapAsync(this);
            return;
        }

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.map_fragment_container, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);
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

                renderMapMarkers();
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
                List<MapLogItem> logs = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String userId = child.child("userID").getValue(String.class);
                    String username = child.child("username").getValue(String.class);
                    String category = child.child("category").getValue(String.class);
                    String itemName = child.child("itemName").getValue(String.class);
                    String locationLabel = child.child("location").getValue(String.class);
                    Double unitsValue = child.child("units").getValue(Double.class);
                    Double latitude = child.child("latitude").getValue(Double.class);
                    Double longitude = child.child("longitude").getValue(Double.class);
                    Long timestamp = child.child("timestamp").getValue(Long.class);

                    if (userId == null || !friendIds.contains(userId)) {
                        continue;
                    }
                    if (!TextUtils.equals(category, LogEntry.Category.Drink.name())) {
                        continue;
                    }
                    if (username == null || latitude == null || longitude == null) {
                        continue;
                    }

                    logs.add(new MapLogItem(
                            child.getKey(),
                            userId,
                            username,
                            TextUtils.isEmpty(itemName) ? "Drink" : itemName,
                            TextUtils.isEmpty(locationLabel) ? "Unknown spot" : locationLabel,
                            unitsValue != null ? unitsValue.floatValue() : 0f,
                            latitude,
                            longitude,
                            timestamp != null ? timestamp : 0L
                    ));
                }

                logs.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
                latestDrinkLogs.clear();
                latestDrinkLogs.addAll(logs);
                updateSubtitle();
                renderMapMarkers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        rootRef.child("consumptionLogs").addValueEventListener(logsListener);
    }

    private void updateSubtitle() {
        if (tvMapSubtitle == null) {
            return;
        }

        if (latestDrinkLogs.isEmpty()) {
            tvMapSubtitle.setText("No recent friend drink locations yet");
        } else {
            tvMapSubtitle.setText("Showing " + latestDrinkLogs.size() + " friend drink logs near you");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.setOnCameraIdleListener(this::renderMapMarkers);
        googleMap.setOnMarkerClickListener(this::handleMarkerClick);
        requestMapLocationIfNeeded();
        enableMyLocationAndCenter();
        renderMapMarkers();
    }

    private void requestMapLocationIfNeeded() {
        if (mapLocationPermissionLauncher != null && !hasLocationPermission()) {
            mapLocationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocationAndCenter() {
        if (googleMap == null || fusedLocationClient == null || !hasLocationPermission()) {
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
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 14.5f));
    }

    private boolean hasLocationPermission() {
        android.content.Context context = getContext();
        if (context == null) {
            return false;
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void renderMapMarkers() {
        if (googleMap == null || !isAdded()) {
            return;
        }

        googleMap.clear();

        if (latestDrinkLogs.isEmpty()) {
            enableMyLocationAndCenter();
            return;
        }

        float zoom = googleMap.getCameraPosition() != null
                ? googleMap.getCameraPosition().zoom
                : 0f;
        List<MapCluster> clusters = buildClusters(latestDrinkLogs, zoom);
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        int markerCount = 0;
        LatLng firstPosition = null;

        for (MapCluster cluster : clusters) {
            LatLng position = cluster.getPosition();
            if (firstPosition == null) {
                firstPosition = position;
            }

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(cluster.getTitle(myUid))
                    .snippet(cluster.getSnippet())
                    .icon(BitmapDescriptorFactory.fromBitmap(buildMarkerBitmap(cluster))));
            if (marker != null) {
                marker.setTag(cluster);
            }

            boundsBuilder.include(position);
            markerCount++;
        }

        if (!hasCenteredMap && markerCount == 1 && firstPosition != null) {
            hasCenteredMap = true;
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPosition, 15f));
        } else if (!hasCenteredMap && markerCount > 1) {
            fitInitialBoundsWhenReady(boundsBuilder.build());
        }

        // Render event markers for events the user created or accepted
        for (EventItem event : myEvents) {
            if (event.latitude == 0 && event.longitude == 0) continue;
            boolean isCreator = myUid.equals(event.createdBy);
            boolean isAccepted = "accepted".equals(event.inviteStatus);
            if (!isCreator && !isAccepted) continue;

            String snippet = EVENT_DATE_FORMAT.format(new Date(event.startTime));
            if (event.locationLabel != null && !event.locationLabel.isEmpty()) {
                snippet += " · " + event.locationLabel;
            }
            Marker eventMarker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(event.latitude, event.longitude))
                    .title(event.title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
            if (eventMarker != null) eventMarker.setTag(event);
        }
    }

    private void fitInitialBoundsWhenReady(LatLngBounds bounds) {
        if (googleMap == null || waitingForInitialBounds) {
            return;
        }

        waitingForInitialBounds = true;
        googleMap.setOnMapLoadedCallback(() -> {
            waitingForInitialBounds = false;
            if (googleMap == null || !isAdded() || hasCenteredMap) {
                return;
            }

            try {
                hasCenteredMap = true;
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dpToPx(72)));
            } catch (IllegalStateException ignored) {
                hasCenteredMap = false;
            }
        });
    }

    private List<MapCluster> buildClusters(List<MapLogItem> logs, float zoom) {
        if (zoom >= INDIVIDUAL_MARKER_ZOOM_THRESHOLD) {
            Map<String, MapCluster> exactLocationClusters = new LinkedHashMap<>();
            for (MapLogItem log : logs) {
                String key = getExactLocationKey(log.latitude, log.longitude);
                MapCluster cluster = exactLocationClusters.get(key);
                if (cluster == null) {
                    cluster = new MapCluster();
                    exactLocationClusters.put(key, cluster);
                }
                cluster.add(log);
            }
            for (MapCluster cluster : exactLocationClusters.values()) {
                if (cluster.logs.size() > 1) {
                    cluster.setLocationSummary(true);
                }
            }
            return new ArrayList<>(exactLocationClusters.values());
        }

        Map<String, MapCluster> clusters = new LinkedHashMap<>();
        double clusterStep = getClusterStepForZoom(zoom);

        for (MapLogItem log : logs) {
            String key = getClusterKey(log.latitude, log.longitude, clusterStep);
            MapCluster cluster = clusters.get(key);
            if (cluster == null) {
                cluster = new MapCluster();
                clusters.put(key, cluster);
            }
            cluster.add(log);
        }

        return new ArrayList<>(clusters.values());
    }

    private double getClusterStepForZoom(float zoom) {
        if (zoom <= 0f) {
            return CLUSTER_STEP;
        }

        double zoomFactor = Math.pow(2d, Math.max(0d, zoom - 14d));
        return Math.max(CLUSTER_STEP / zoomFactor, CLUSTER_STEP / 8d);
    }

    private String getClusterKey(double latitude, double longitude, double clusterStep) {
        long latBucket = Math.round(latitude / clusterStep);
        long lngBucket = Math.round(longitude / clusterStep);
        return latBucket + ":" + lngBucket;
    }

    private String getExactLocationKey(double latitude, double longitude) {
        return Double.doubleToLongBits(latitude) + ":" + Double.doubleToLongBits(longitude);
    }

    private Bitmap buildMarkerBitmap(MapCluster cluster) {
        if (cluster.isLocationSummary()) {
            return buildLocationSummaryBitmap(cluster);
        }
        return buildPinBitmap(cluster);
    }

    private Bitmap buildPinBitmap(MapCluster cluster) {
        int canvasWidth = dpToPx(80);
        int canvasHeight = dpToPx(106);
        int photoDiameter = dpToPx(DEFAULT_PIN_PHOTO_DIAMETER_DP);
        int strokeWidth = dpToPx(4);
        int centerX = canvasWidth / 2;
        int photoTop = dpToPx(6);
        int photoLeft = centerX - (photoDiameter / 2);

        Bitmap output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float circleRadius = photoDiameter / 2f;
        float circleCenterX = centerX;
        float circleCenterY = photoTop + circleRadius;

        Path pointer = new Path();
        pointer.moveTo(centerX, canvasHeight - dpToPx(8));
        pointer.lineTo(centerX - dpToPx(14), photoTop + photoDiameter - dpToPx(2));
        pointer.lineTo(centerX + dpToPx(14), photoTop + photoDiameter - dpToPx(2));
        pointer.close();

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(pointer, paint);
        canvas.drawCircle(circleCenterX, circleCenterY, circleRadius + strokeWidth, paint);

        Bitmap photo = getProfileBitmap(cluster.representative.userId);
        if (photo == null) {
            photo = createPlaceholderProfileBitmap(photoDiameter);
        }
        Rect srcRect = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        RectF dstRect = new RectF(photoLeft, photoTop, photoLeft + photoDiameter, photoTop + photoDiameter);
        Path clipPath = new Path();
        clipPath.addOval(dstRect, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawBitmap(photo, srcRect, dstRect, null);
        canvas.restore();

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        canvas.drawCircle(circleCenterX, circleCenterY, circleRadius + (strokeWidth / 2f), paint);

        if (cluster.logs.size() > 1) {
            drawCountBadge(canvas, cluster.logs.size(), canvasWidth - dpToPx(18), photoTop + dpToPx(10));
        }

        return output;
    }

    private Bitmap buildLocationSummaryBitmap(MapCluster cluster) {
        int canvasWidth = dpToPx(80);
        int canvasHeight = dpToPx(80);
        int circleDiameter = dpToPx(56);
        int centerX = canvasWidth / 2;
        int centerY = canvasHeight / 2;
        int radius = circleDiameter / 2;

        Bitmap output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(3));
        paint.setColor(Color.parseColor("#111111"));
        canvas.drawCircle(centerX, centerY, radius - dpToPx(1), paint);

        int dotRadius = dpToPx(4);
        int dotSpacing = dpToPx(11);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#111111"));
        canvas.drawCircle(centerX - dotSpacing, centerY, dotRadius, paint);
        canvas.drawCircle(centerX, centerY, dotRadius, paint);
        canvas.drawCircle(centerX + dotSpacing, centerY, dotRadius, paint);

        drawCountBadge(canvas, cluster.logs.size(), canvasWidth - dpToPx(18), dpToPx(18));
        return output;
    }

    private boolean handleMarkerClick(Marker marker) {
        Object tag = marker.getTag();
        if (tag instanceof EventItem) {
            showEventDetailDialog((EventItem) tag);
            return true;
        }
        if (!(tag instanceof MapCluster)) {
            return false;
        }

        MapCluster cluster = (MapCluster) tag;
        if (!cluster.isLocationSummary()) {
            return false;
        }

        showLocationLogsDialog(cluster);
        return true;
    }

    private void showLocationLogsDialog(MapCluster cluster) {
        if (!isAdded()) {
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("pollution logged at this location")
                .setView(buildLocationLogsDialogView(cluster))
                .setPositiveButton("Close", null)
                .show();
    }

    private View buildLocationLogsDialogView(MapCluster cluster) {
        Context context = requireContext();

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dpToPx(8);
        int verticalPadding = dpToPx(4);
        container.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        for (int i = 0; i < cluster.logs.size(); i++) {
            if (i > 0) {
                View divider = new View(context);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                );
                dividerParams.topMargin = dpToPx(6);
                dividerParams.bottomMargin = dpToPx(6);
                divider.setLayoutParams(dividerParams);
                divider.setBackgroundColor(Color.parseColor("#D7D7D7"));
                container.addView(divider);
            }

            container.addView(buildLocationLogRow(cluster.logs.get(i)));
        }

        scrollView.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scrollView;
    }

    private View buildLocationLogRow(MapLogItem log) {
        Context context = requireContext();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView imageView = new ImageView(context);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(42));
        imageParams.rightMargin = dpToPx(12);
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageBitmap(getProfileBitmap(log.userId));
        row.addView(imageView);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView nameView = new TextView(context);
        nameView.setText(TextUtils.equals(log.userId, myUid) ? "You" : log.username);
        nameView.setTextColor(Color.BLACK);
        nameView.setTextSize(16);
        nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
        textContainer.addView(nameView);

        TextView drinkView = new TextView(context);
        drinkView.setText(log.itemName);
        drinkView.setTextColor(Color.BLACK);
        drinkView.setTextSize(15);
        drinkView.setPadding(0, dpToPx(2), 0, 0);
        textContainer.addView(drinkView);

        row.addView(textContainer);
        return row;
    }

    private void drawCountBadge(Canvas canvas, int count, int centerX, int centerY) {
        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(Color.parseColor("#111111"));
        canvas.drawCircle(centerX, centerY, dpToPx(11), badgePaint);

        badgePaint.setStyle(Paint.Style.STROKE);
        badgePaint.setColor(Color.WHITE);
        badgePaint.setStrokeWidth(dpToPx(2));
        canvas.drawCircle(centerX, centerY, dpToPx(11), badgePaint);

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(Color.WHITE);
        badgePaint.setTextAlign(Paint.Align.CENTER);
        badgePaint.setTextSize(dpToPx(11));
        badgePaint.setFakeBoldText(true);

        String label = count > 99 ? "99+" : String.valueOf(count);
        Paint.FontMetrics metrics = badgePaint.getFontMetrics();
        float baseline = centerY - ((metrics.ascent + metrics.descent) / 2f);
        canvas.drawText(label, centerX, baseline, badgePaint);
    }

    private Bitmap getProfileBitmap(String userId) {
        String imageUrl = profilePictureUrlsByUserId.get(userId);
        if (TextUtils.isEmpty(imageUrl)) {
            return getFallbackProfileBitmap();
        }

        Bitmap cached = rawProfileBitmapCache.get(imageUrl);
        if (cached != null) {
            return cached;
        }

        synchronized (pendingProfileLoads) {
            if (pendingProfileLoads.contains(imageUrl)) {
                return getFallbackProfileBitmap();
            }
            pendingProfileLoads.add(imageUrl);
        }

        imageExecutor.execute(() -> {
            try (InputStream input = new URL(imageUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null) {
                    rawProfileBitmapCache.put(imageUrl, bitmap);
                    android.app.Activity activity = getActivity();
                    if (isAdded() && activity != null) {
                        activity.runOnUiThread(this::renderMapMarkers);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                synchronized (pendingProfileLoads) {
                    pendingProfileLoads.remove(imageUrl);
                }
            }
        });
        return getFallbackProfileBitmap();
    }

    private Bitmap getFallbackProfileBitmap() {
        return createPlaceholderProfileBitmap(dpToPx(DEFAULT_PIN_PHOTO_DIAMETER_DP));
    }

    private Bitmap createPlaceholderProfileBitmap(int size) {
        int resolvedSize = Math.max(size, dpToPx(DEFAULT_PIN_PHOTO_DIAMETER_DP));
        Bitmap bitmap = Bitmap.createBitmap(resolvedSize, resolvedSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.parseColor("#C96A46"));
        canvas.drawCircle(resolvedSize / 2f, resolvedSize / 2f, resolvedSize / 2f, fillPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(resolvedSize * 0.42f);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = (resolvedSize / 2f) - ((metrics.ascent + metrics.descent) / 2f);
        canvas.drawText("?", resolvedSize / 2f, baseline, textPaint);
        return bitmap;
    }

    // ─── Events ──────────────────────────────────────────────────────────────

    private void listenToEvents() {
        eventsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                myEvents.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String createdBy = child.child("createdBy").getValue(String.class);
                    boolean isCreator = myUid.equals(createdBy);
                    DataSnapshot inviteSnap = child.child("invites").child(myUid);
                    boolean isInvited = inviteSnap.exists();

                    if (!isCreator && !isInvited) continue;

                    String id = child.getKey();
                    String title = child.child("title").getValue(String.class);
                    Long date = child.child("date").getValue(Long.class);
                    Long startTime = child.child("startTime").getValue(Long.class);
                    Double lat = child.child("latitude").getValue(Double.class);
                    Double lng = child.child("longitude").getValue(Double.class);
                    String locLabel = child.child("locationLabel").getValue(String.class);

                    if (title == null || date == null) continue;

                    EventItem event = new EventItem(
                            id, title, date,
                            startTime != null ? startTime : date, date);
                    event.createdBy = createdBy;
                    event.latitude = lat != null ? lat : 0;
                    event.longitude = lng != null ? lng : 0;
                    event.locationLabel = TextUtils.isEmpty(locLabel) ? "" : locLabel;

                    if (isCreator) {
                        event.inviteStatus = "accepted";
                    } else {
                        Object statusVal = inviteSnap.child("status").getValue();
                        if (statusVal instanceof String) {
                            event.inviteStatus = (String) statusVal;
                        } else {
                            // Legacy: boolean true written before the status-map fix
                            event.inviteStatus = "pending";
                        }
                        Object canInviteVal = inviteSnap.child("canInvite").getValue();
                        event.canInvite = Boolean.TRUE.equals(canInviteVal);
                    }

                    myEvents.add(event);
                }
                if (googleMap != null) renderMapMarkers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        rootRef.child("Events").addValueEventListener(eventsListener);
    }

    public void enterLocationPickMode() {
        if (locationPickOverlay == null) return;
        isLocationPickMode = true;
        locationPickOverlay.setVisibility(View.VISIBLE);
        if (getView() != null) {
            getView().findViewById(R.id.btn_map_events).setVisibility(View.GONE);
        }
    }

    private void reopenCreateEvent() {
        new CreateEventFragment().show(getChildFragmentManager(), "create_event");
    }

    private String getLocationLabel(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String name = addr.getFeatureName();
                String street = addr.getThoroughfare();
                String city = addr.getLocality();
                if (name != null && !name.matches("\\d+") && !name.equals(street)) return name;
                if (street != null) return city != null ? street + ", " + city : street;
                if (city != null) return city;
            }
        } catch (Exception ignored) {}
        return String.format(Locale.US, "%.4f, %.4f", lat, lng);
    }

    private void showEventsDialog() {
        if (!isAdded()) return;
        Context context = requireContext();

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Events")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        Button btnCreate = new Button(context);
        btnCreate.setText("+ Create Event");
        btnCreate.setAllCaps(false);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        createParams.bottomMargin = dpToPx(8);
        btnCreate.setLayoutParams(createParams);
        btnCreate.setOnClickListener(v -> {
            dialog.dismiss();
            new CreateEventFragment().show(getChildFragmentManager(), "create_event");
        });
        container.addView(btnCreate);

        if (myEvents.isEmpty()) {
            TextView tvEmpty = new TextView(context);
            tvEmpty.setText("No events yet. Create one!");
            tvEmpty.setTextColor(Color.parseColor("#888888"));
            tvEmpty.setTextSize(14);
            tvEmpty.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            container.addView(tvEmpty);
        } else {
            for (int i = 0; i < myEvents.size(); i++) {
                if (i > 0) {
                    View divider = new View(context);
                    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
                    dp.topMargin = dpToPx(4);
                    dp.bottomMargin = dpToPx(4);
                    divider.setLayoutParams(dp);
                    divider.setBackgroundColor(Color.parseColor("#D7D7D7"));
                    container.addView(divider);
                }
                container.addView(buildEventRow(context, myEvents.get(i), dialog));
            }
        }

        scrollView.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.show();
    }

    private View buildEventRow(Context context, EventItem event, AlertDialog parentDialog) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));

        TextView tvTitle = new TextView(context);
        tvTitle.setText(event.title);
        tvTitle.setTextColor(Color.BLACK);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(tvTitle);

        TextView tvDate = new TextView(context);
        tvDate.setText(EVENT_DATE_FORMAT.format(new Date(event.startTime)));
        tvDate.setTextColor(Color.parseColor("#888888"));
        tvDate.setTextSize(13);
        tvDate.setPadding(0, dpToPx(2), 0, 0);
        row.addView(tvDate);

        if (!TextUtils.isEmpty(event.locationLabel)) {
            TextView tvLoc = new TextView(context);
            tvLoc.setText(event.locationLabel);
            tvLoc.setTextColor(Color.parseColor("#555555"));
            tvLoc.setTextSize(13);
            tvLoc.setPadding(0, dpToPx(2), 0, 0);
            row.addView(tvLoc);
        }

        boolean isCreator = myUid.equals(event.createdBy);
        if (isCreator) {
            TextView tvStatus = new TextView(context);
            tvStatus.setText("You created this");
            tvStatus.setTextColor(Color.parseColor("#555555"));
            tvStatus.setTextSize(13);
            tvStatus.setPadding(0, dpToPx(4), 0, 0);
            row.addView(tvStatus);
        } else if ("accepted".equals(event.inviteStatus)) {
            TextView tvStatus = new TextView(context);
            tvStatus.setText("Attending");
            tvStatus.setTextColor(Color.parseColor("#2E7D32"));
            tvStatus.setTextSize(13);
            tvStatus.setPadding(0, dpToPx(4), 0, 0);
            row.addView(tvStatus);
        } else if ("declined".equals(event.inviteStatus)) {
            TextView tvStatus = new TextView(context);
            tvStatus.setText("Declined");
            tvStatus.setTextColor(Color.parseColor("#888888"));
            tvStatus.setTextSize(13);
            tvStatus.setPadding(0, dpToPx(4), 0, 0);
            row.addView(tvStatus);
        } else {
            // pending
            LinearLayout btnRow = new LinearLayout(context);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, dpToPx(4), 0, 0);

            Button btnAccept = new Button(context);
            btnAccept.setText("Accept");
            btnAccept.setAllCaps(false);

            Button btnDecline = new Button(context);
            btnDecline.setText("Decline");
            btnDecline.setAllCaps(false);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.setMarginStart(dpToPx(8));
            btnDecline.setLayoutParams(dlp);

            btnAccept.setOnClickListener(v -> { handleAccept(event); parentDialog.dismiss(); });
            btnDecline.setOnClickListener(v -> { handleDecline(event); parentDialog.dismiss(); });

            btnRow.addView(btnAccept);
            btnRow.addView(btnDecline);
            row.addView(btnRow);
        }

        return row;
    }

    private void showEventDetailDialog(EventItem event) {
        if (!isAdded()) return;
        Context context = requireContext();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8));

        TextView tvDate = new TextView(context);
        tvDate.setText(EVENT_DATE_FORMAT.format(new Date(event.startTime)));
        tvDate.setTextColor(Color.parseColor("#888888"));
        tvDate.setTextSize(14);
        container.addView(tvDate);

        if (!TextUtils.isEmpty(event.locationLabel)) {
            TextView tvLoc = new TextView(context);
            tvLoc.setText(event.locationLabel);
            tvLoc.setTextColor(Color.BLACK);
            tvLoc.setTextSize(14);
            tvLoc.setPadding(0, dpToPx(4), 0, 0);
            container.addView(tvLoc);
        }

        boolean isCreator = myUid.equals(event.createdBy);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(event.title)
                .setView(container)
                .setNegativeButton("Close", null);

        if (isCreator) {
            TextView tvStatus = new TextView(context);
            tvStatus.setText("You created this");
            tvStatus.setTextColor(Color.parseColor("#555555"));
            tvStatus.setTextSize(14);
            tvStatus.setPadding(0, dpToPx(8), 0, 0);
            container.addView(tvStatus);
            builder.show();
        } else if ("accepted".equals(event.inviteStatus)) {
            TextView tvStatus = new TextView(context);
            tvStatus.setText("Attending");
            tvStatus.setTextColor(Color.parseColor("#2E7D32"));
            tvStatus.setTextSize(14);
            tvStatus.setPadding(0, dpToPx(8), 0, 0);
            container.addView(tvStatus);
            builder.show();
        } else if ("declined".equals(event.inviteStatus)) {
            TextView tvStatus = new TextView(context);
            tvStatus.setText("Declined");
            tvStatus.setTextColor(Color.parseColor("#888888"));
            tvStatus.setTextSize(14);
            tvStatus.setPadding(0, dpToPx(8), 0, 0);
            container.addView(tvStatus);
            builder.show();
        } else {
            // pending — show accept/decline
            AlertDialog dialog = builder.create();
            LinearLayout btnRow = new LinearLayout(context);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, dpToPx(8), 0, 0);

            Button btnAccept = new Button(context);
            btnAccept.setText("Accept");
            btnAccept.setAllCaps(false);
            btnAccept.setOnClickListener(v -> { handleAccept(event); dialog.dismiss(); });

            Button btnDecline = new Button(context);
            btnDecline.setText("Decline");
            btnDecline.setAllCaps(false);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.setMarginStart(dpToPx(8));
            btnDecline.setLayoutParams(dlp);
            btnDecline.setOnClickListener(v -> { handleDecline(event); dialog.dismiss(); });

            btnRow.addView(btnAccept);
            btnRow.addView(btnDecline);
            container.addView(btnRow);
            dialog.show();
        }
    }

    private void handleAccept(EventItem event) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "accepted");
        rootRef.child("Events").child(event.id).child("invites").child(myUid)
                .updateChildren(update);
        rootRef.child("Events").child(event.id).child("attendees").child(myUid)
                .setValue(true);
        event.inviteStatus = "accepted";
        renderMapMarkers();
    }

    private void handleDecline(EventItem event) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "declined");
        rootRef.child("Events").child(event.id).child("invites").child(myUid)
                .updateChildren(update);
        event.inviteStatus = "declined";
        renderMapMarkers();
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
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
        if (rootRef != null && eventsListener != null) {
            rootRef.child("Events").removeEventListener(eventsListener);
        }
        tvMapSubtitle = null;
        locationPickOverlay = null;
        isLocationPickMode = false;
        waitingForInitialBounds = false;
        googleMap = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        imageExecutor.shutdownNow();
    }

    private static class MapLogItem {
        final String postId;
        final String userId;
        final String username;
        final String itemName;
        final String locationLabel;
        final float units;
        final double latitude;
        final double longitude;
        final long timestamp;

        MapLogItem(String postId, String userId, String username, String itemName,
                   String locationLabel, float units, double latitude, double longitude, long timestamp) {
            this.postId = postId;
            this.userId = userId;
            this.username = username;
            this.itemName = itemName;
            this.locationLabel = locationLabel;
            this.units = units;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
    }

    private static class MapCluster {
        private final List<MapLogItem> logs = new ArrayList<>();
        private MapLogItem representative;
        private double latitudeSum;
        private double longitudeSum;
        private boolean locationSummary;

        void add(MapLogItem log) {
            logs.add(log);
            latitudeSum += log.latitude;
            longitudeSum += log.longitude;
            if (representative == null || log.timestamp > representative.timestamp) {
                representative = log;
            }
        }

        LatLng getPosition() {
            return new LatLng(latitudeSum / logs.size(), longitudeSum / logs.size());
        }

        void setLocationSummary(boolean locationSummary) {
            this.locationSummary = locationSummary;
        }

        boolean isLocationSummary() {
            return locationSummary;
        }

        String getTitle(String myUid) {
            if (logs.size() == 1 && representative != null) {
                String name = TextUtils.equals(representative.userId, myUid) ? "You" : representative.username;
                return name + " drank " + representative.itemName;
            }
            return logs.size() + " drinks near " + getPrimaryLocation();
        }

        String getSnippet() {
            if (locationSummary) {
                return "Tap to view all drinks from this location";
            }
            if (logs.size() == 1 && representative != null) {
                return representative.locationLabel + " - " + representative.units + " units";
            }
            return getPrimaryLocation() + " - Friends only";
        }

        private String getPrimaryLocation() {
            return representative == null ? "this spot" : representative.locationLabel;
        }
    }
}
