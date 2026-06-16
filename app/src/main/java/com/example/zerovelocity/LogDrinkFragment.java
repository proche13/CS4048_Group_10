package com.example.zerovelocity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// This fragment is the log entry screen.
// The user picks a category (Drink or Cigarette), fills in the details
// and takes or picks a photo before submitting. The photo is uploaded to
// Firebase Storage and the rest of the data goes to the Realtime Database
// through LogDrinkViewModel and LogRepo.
public class LogDrinkFragment extends Fragment {

    // UI views
    private MaterialButtonToggleGroup toggleCategory;
    private TextInputLayout tilDrinkType, tilItemName, tilSpiritMeasure, tilMixer, tilUnits;
    private AutoCompleteTextView etDrinkType, etItemName, etSpiritMeasure, etMixer;
    private TextInputEditText etUnits, etLocation, etDescription;
    private ImageView ivPreview;                     // shows a preview of the selected photo
    private View llPhotoPlaceholder;                 // the camera icon shown before a photo is picked
    private MaterialButton btnLog;

    private LogDrinkViewModel viewModel;
    private FusedLocationProviderClient fusedLocationClient;

    // keeps track of which category the user currently has selected
    private LogEntry.Category currentCategory = LogEntry.Category.Drink;

    // the URI of the photo the user has selected, either from gallery or camera
    private Uri selectedImageUri;

    // we need to store the camera URI separately so we can access it in the camera result callback
    private Uri cameraImageUri;
    private Runnable pendingLocationSubmit;

    // activity result launchers have to be registered in onCreate, not onCreateView
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // opens the gallery and lets the user pick any image
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) setImagePreview(uri); });

        // takes a photo and saves it to cameraImageUri which we created before launching
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> { if (success && cameraImageUri != null) setImagePreview(cameraImageUri); });

        // asks the user for camera permission, then opens the camera if they say yes
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) openCamera(); else
                    Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show(); });

        // asks for device location so logs can be plotted on the feed map
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean granted = Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted && pendingLocationSubmit != null) {
                        Runnable submit = pendingLocationSubmit;
                        pendingLocationSubmit = null;
                        submit.run();
                    } else {
                        pendingLocationSubmit = null;
                        Toast.makeText(requireContext(), "Location is needed for map pins.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log_drink, container, false);

        // grab all the views from the layout
        toggleCategory     = view.findViewById(R.id.toggle_category);
        tilDrinkType       = view.findViewById(R.id.til_drink_type);
        tilItemName        = view.findViewById(R.id.til_item_name);
        tilSpiritMeasure   = view.findViewById(R.id.til_spirit_measure);
        tilMixer           = view.findViewById(R.id.til_mixer);
        tilUnits           = view.findViewById(R.id.til_units);
        etDrinkType        = view.findViewById(R.id.et_drink_type);
        etItemName         = view.findViewById(R.id.et_item_name);
        etSpiritMeasure    = view.findViewById(R.id.et_spirit_measure);
        etMixer            = view.findViewById(R.id.et_mixer);
        etUnits            = view.findViewById(R.id.et_units);
        etLocation         = view.findViewById(R.id.et_location);
        etDescription      = view.findViewById(R.id.et_description);
        ivPreview          = view.findViewById(R.id.iv_preview);
        llPhotoPlaceholder = view.findViewById(R.id.ll_photo_placeholder);
        btnLog             = view.findViewById(R.id.btn_log);

        viewModel = new ViewModelProvider(this).get(LogDrinkViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        setupControlledPickers();

        // when the user switches category, the controlled fields are reset to valid options
        toggleCategory.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_log_drink)           currentCategory = LogEntry.Category.Drink;
            else if (checkedId == R.id.btn_log_cigarette)  currentCategory = LogEntry.Category.Cigarette;
            updateHintsForCategory();
        });

        // gallery button just fires off the gallery launcher
        view.findViewById(R.id.btn_gallery).setOnClickListener(v ->
                galleryLauncher.launch("image/*"));

        // camera button checks for permission first, requests it if we dont have it yet
        view.findViewById(R.id.btn_camera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        btnLog.setOnClickListener(v -> submit());

        return view;
    }

    // sets up fixed choices so users cannot type arbitrary item names or units
    private void setupControlledPickers() {
        setDropdown(etDrinkType, Arrays.asList("Beer", "Cider", "Stout", "Spirit"));
        setDropdown(etSpiritMeasure, Arrays.asList("Single", "Double"));
        setDropdown(etMixer, Arrays.asList(
                "None", "Coca-Cola", "Diet Coke", "Coke Zero", "7UP", "Club Orange",
                "MiWadi Blackcurrant", "MiWadi Orange", "Tonic Water", "Soda Water",
                "Ginger Ale", "Red Bull", "Orange Juice", "Cranberry Juice"));

        etDrinkType.setOnItemClickListener((parent, view, position, id) -> {
            updateDrinkBrandOptions(etDrinkType.getText().toString());
            updateCalculatedUnits();
        });
        etItemName.setOnItemClickListener((parent, view, position, id) -> updateCalculatedUnits());
        etSpiritMeasure.setOnItemClickListener((parent, view, position, id) -> updateCalculatedUnits());

        updateHintsForCategory();
    }

    private void setDropdown(AutoCompleteTextView view, List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, values);
        view.setAdapter(adapter);
        view.setOnClickListener(v -> view.showDropDown());
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) view.showDropDown();
        });
    }

    // updates the visible fields to match drink vs cigarette logging
    private void updateHintsForCategory() {
        clearPickerErrors();
        switch (currentCategory) {
            case Drink:
                tilDrinkType.setVisibility(View.VISIBLE);
                tilSpiritMeasure.setVisibility(isSpiritSelected() ? View.VISIBLE : View.GONE);
                tilMixer.setVisibility(isSpiritSelected() ? View.VISIBLE : View.GONE);
                tilUnits.setVisibility(View.GONE);
                tilItemName.setHint("Drink");
                tilUnits.setHint("Calculated drink units");
                if (TextUtils.isEmpty(etDrinkType.getText())) {
                    etItemName.setText("", false);
                } else {
                    updateDrinkBrandOptions(etDrinkType.getText().toString());
                }
                break;
            case Cigarette:
                tilDrinkType.setVisibility(View.GONE);
                tilSpiritMeasure.setVisibility(View.GONE);
                tilMixer.setVisibility(View.GONE);
                tilUnits.setVisibility(View.GONE);
                tilItemName.setHint("Brand");
                tilUnits.setHint("Cigarette units");
                setDropdown(etItemName, Arrays.asList(
                        "Marlboro Gold", "Marlboro Red", "Benson & Hedges Gold",
                        "John Player Blue", "Silk Cut Blue", "Silk Cut Purple",
                        "Camel Blue", "Mayfair", "L&M", "Amber Leaf"));
                etItemName.setText("", false);
                break;
        }
        updateCalculatedUnits();
    }

    private void updateDrinkBrandOptions(String drinkType) {
        List<String> drinks;
        switch (drinkType) {
            case "Cider":
                drinks = Arrays.asList("Bulmers", "Orchard Thieves", "Kopparberg", "Magners", "Cronins");
                break;
            case "Stout":
                drinks = Arrays.asList("Guinness", "Murphy's", "Beamish", "O'Hara's Irish Stout", "Island's Edge");
                break;
            case "Spirit":
                drinks = Arrays.asList(
                        "Jameson", "Powers", "Tullamore D.E.W.", "Smirnoff Vodka",
                        "Gordon's Gin", "Captain Morgan", "Bacardi", "Baileys", "Hennessy");
                break;
            case "Beer":
            default:
                drinks = Arrays.asList(
                        "Heineken", "Coors", "Rockshore", "Corona", "Budweiser",
                        "Carlsberg", "Harp", "Smithwick's", "Birra Moretti", "Peroni");
                break;
        }

        setDropdown(etItemName, drinks);
        etItemName.setText("", false);
        boolean spirit = "Spirit".equals(drinkType);
        tilSpiritMeasure.setVisibility(spirit ? View.VISIBLE : View.GONE);
        tilMixer.setVisibility(spirit ? View.VISIBLE : View.GONE);
        etSpiritMeasure.setText(spirit ? "Single" : "", false);
        etMixer.setText(spirit ? "None" : "", false);
    }

    private boolean isSpiritSelected() {
        return "Spirit".equals(etDrinkType.getText().toString());
    }

    private void updateCalculatedUnits() {
        if (currentCategory == LogEntry.Category.Cigarette) {
            etUnits.setText("1");
        } else if (isSpiritSelected()) {
            etUnits.setText("Double".equals(etSpiritMeasure.getText().toString()) ? "2" : "1");
        } else if (TextUtils.isEmpty(etDrinkType.getText())) {
            etUnits.setText("");
        } else {
            etUnits.setText("1");
        }
    }

    private void clearPickerErrors() {
        tilDrinkType.setError(null);
        tilItemName.setError(null);
        tilSpiritMeasure.setError(null);
        tilMixer.setError(null);
        tilUnits.setError(null);
    }

    // stores the chosen URI and swaps the placeholder out for the actual image preview
    private void setImagePreview(Uri uri) {
        selectedImageUri = uri;
        ivPreview.setImageURI(uri);
        ivPreview.setVisibility(View.VISIBLE);
        llPhotoPlaceholder.setVisibility(View.GONE);
    }

    // creates a temporary file in the app cache for the camera to write to
    // FileProvider is needed to give the camera app permission to write to our private cache
    private void openCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "images");
            dir.mkdirs();
            File tmp = File.createTempFile("img_", ".jpg", dir);
            cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    tmp);
            cameraLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Could not open camera", Toast.LENGTH_SHORT).show();
        }
    }

    // runs when the user taps Log Entry
    // validates all the fields, uploads the photo to Firebase Storage,
    // then saves the log entry to the database once we have the download URL
    private void submit() {
        // read all the field values up front
        String itemName    = buildLoggedItemName();
        String unitsStr    = etUnits.getText()       != null ? etUnits.getText().toString().trim()       : "";
        String location    = etLocation.getText()    != null ? etLocation.getText().toString().trim()    : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        if (!validateControlledChoices()) {
            return;
        }

        if (TextUtils.isEmpty(location)) {
            etLocation.setError("Spot name is required");
            return;
        }

        if (unitsStr.isEmpty()) {
            tilUnits.setError("Calculated units missing");
            return;
        }
        float units;
        try {
            units = Float.parseFloat(unitsStr);
        } catch (NumberFormatException e) {
            tilUnits.setError("Enter a valid number");
            return;
        }
        tilUnits.setError(null);

        // photo is required, the user must pick or take one before submitting
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Please add a photo", Toast.LENGTH_SHORT).show();
            return;
        }

        // disable the button and show uploading text so the user knows something is happening
        setBusy(true);

        // build the storage path: logs/userID/randomID.jpg
        String uid     = viewModel.getCurrentUid();
        String eventId = UUID.randomUUID().toString();

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference("logs/" + uid + "/" + eventId + ".jpg");

        // these need to be final so they can be used inside the callbacks below
        final float  finalUnits    = units;
        final String finalItemName = itemName;
        final String finalLocation = location;
        final String finalDesc     = description;

        if (!hasLocationPermission()) {
            pendingLocationSubmit = () -> {
                setBusy(true);
                getCurrentLocation(locationResult ->
                        uploadLogWithLocation(storageRef, finalItemName, finalUnits, finalDesc,
                                finalLocation, locationResult));
            };
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        // step 1: get the current coordinates for the map
        // step 2: upload the photo to Firebase Storage
        // step 3: once uploaded, get the public download URL
        // step 4: save the log entry to the database with photo and coordinates attached
        getCurrentLocation(locationResult ->
                uploadLogWithLocation(storageRef, finalItemName, finalUnits, finalDesc,
                        finalLocation, locationResult));
    }

    private boolean validateControlledChoices() {
        clearPickerErrors();
        if (currentCategory == LogEntry.Category.Drink && TextUtils.isEmpty(etDrinkType.getText())) {
            tilDrinkType.setError("Choose a drink type");
            return false;
        }
        if (TextUtils.isEmpty(etItemName.getText())) {
            tilItemName.setError(currentCategory == LogEntry.Category.Drink
                    ? "Choose a drink" : "Choose a brand");
            return false;
        }
        if (currentCategory == LogEntry.Category.Drink && isSpiritSelected()) {
            if (TextUtils.isEmpty(etSpiritMeasure.getText())) {
                tilSpiritMeasure.setError("Choose single or double");
                return false;
            }
            if (TextUtils.isEmpty(etMixer.getText())) {
                tilMixer.setError("Choose a mixer");
                return false;
            }
        }
        updateCalculatedUnits();
        return true;
    }

    private String buildLoggedItemName() {
        String item = etItemName.getText() != null ? etItemName.getText().toString().trim() : "";
        if (currentCategory == LogEntry.Category.Drink && isSpiritSelected()) {
            String measure = etSpiritMeasure.getText().toString();
            String mixer = etMixer.getText().toString();
            return measure + " " + item + ("None".equals(mixer) ? "" : " with " + mixer);
        }
        return item;
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation(OnLocationReady callback) {
        if (!hasLocationPermission()) {
            callback.onReady(null);
            return;
        }

        AtomicBoolean completed = new AtomicBoolean(false);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());

        Runnable timeout = () -> {
            if (completed.compareAndSet(false, true)) {
                Toast.makeText(requireContext(), "Location unavailable. Log saved without map pin.",
                        Toast.LENGTH_LONG).show();
                callback.onReady(null);
            }
        };
        timeoutHandler.postDelayed(timeout, 5000);

        // Last known location is usually instant. If it is missing, ask for a fresh fix.
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(lastLocation -> {
                    if (lastLocation != null && completed.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacks(timeout);
                        callback.onReady(lastLocation);
                    } else if (lastLocation == null) {
                        requestFreshLocation(callback, completed, timeoutHandler, timeout);
                    }
                })
                .addOnFailureListener(e ->
                        requestFreshLocation(callback, completed, timeoutHandler, timeout));
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation(OnLocationReady callback, AtomicBoolean completed,
                                      Handler timeoutHandler, Runnable timeout) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (completed.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacks(timeout);
                        if (location == null) {
                            Toast.makeText(requireContext(),
                                    "Location unavailable. Log saved without map pin.",
                                    Toast.LENGTH_LONG).show();
                        }
                        callback.onReady(location);
                    }
                })
                .addOnFailureListener(e -> {
                    if (completed.compareAndSet(false, true)) {
                        timeoutHandler.removeCallbacks(timeout);
                        Toast.makeText(requireContext(),
                                "Location unavailable. Log saved without map pin.",
                                Toast.LENGTH_LONG).show();
                        callback.onReady(null);
                    }
                });
    }

    private void uploadLogWithLocation(StorageReference storageRef, String itemName, float units,
                                       String description, String locationLabel,
                                       Location locationResult) {
        Double latitude = locationResult != null ? locationResult.getLatitude() : null;
        Double longitude = locationResult != null ? locationResult.getLongitude() : null;

        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(snap ->
                        snap.getStorage().getDownloadUrl()
                                .addOnSuccessListener(downloadUri -> {
                                    viewModel.logEvent(currentCategory, itemName, units,
                                            description, locationLabel, latitude, longitude,
                                            downloadUri.toString());
                                    Toast.makeText(requireContext(), "Logged!", Toast.LENGTH_SHORT).show();
                                    clearForm();
                                })
                                .addOnFailureListener(this::onUploadError))
                .addOnFailureListener(this::onUploadError);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // called when anything in the upload or download URL step fails
    // logs the full error so it is easy to debug in Logcat
    private void onUploadError(Exception e) {
        setBusy(false);
        String msg = e != null ? e.getMessage() : "unknown error";
        Toast.makeText(requireContext(), "Upload failed: " + msg, Toast.LENGTH_LONG).show();
        android.util.Log.e("LogDrink", "Upload error: " + msg);
    }

    // disables the log button and changes its text while the upload is in progress
    private void setBusy(boolean busy) {
        btnLog.setEnabled(!busy);
        btnLog.setText(busy ? "Uploading..." : "Log Entry");
    }

    // resets all the fields back to their default state after a successful log
    private void clearForm() {
        etItemName.setText("");
        etUnits.setText("");
        etDrinkType.setText("");
        etSpiritMeasure.setText("");
        etMixer.setText("");
        etLocation.setText("");
        etDescription.setText("");
        selectedImageUri = null;
        ivPreview.setVisibility(View.GONE);
        llPhotoPlaceholder.setVisibility(View.VISIBLE);
        updateHintsForCategory();
        setBusy(false);
    }

    private interface OnLocationReady {
        void onReady(Location location);
    }
}
