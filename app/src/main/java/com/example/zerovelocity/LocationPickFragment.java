package com.example.zerovelocity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.util.List;
import java.util.Locale;

public class LocationPickFragment extends DialogFragment implements OnMapReadyCallback {

    static final String RESULT_KEY = "location_pick";

    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_ZeroVelocity);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_location_pick, container, false);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getChildFragmentManager().beginTransaction()
                .replace(R.id.lp_map_container, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);

        view.findViewById(R.id.btn_lp_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_lp_confirm).setOnClickListener(v -> {
            if (googleMap == null) return;
            LatLng center = googleMap.getCameraPosition().target;
            Bundle result = new Bundle();
            result.putDouble("lat", center.latitude);
            result.putDouble("lng", center.longitude);
            result.putString("label", getLocationLabel(center.latitude, center.longitude));
            requireActivity().getSupportFragmentManager()
                    .setFragmentResult(RESULT_KEY, result);
            dismiss();
        });

        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(hasLocationPermission());
        centerOnUserLocation();
    }

    @SuppressLint("MissingPermission")
    private void centerOnUserLocation() {
        if (fusedLocationClient == null || !hasLocationPermission()) return;
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null && googleMap != null && isAdded()) {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(location.getLatitude(), location.getLongitude()), 15f));
                    }
                })
                .addOnFailureListener(e -> fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(location -> {
                            if (location != null && googleMap != null && isAdded()) {
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(location.getLatitude(), location.getLongitude()), 15f));
                            }
                        }));
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
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
}