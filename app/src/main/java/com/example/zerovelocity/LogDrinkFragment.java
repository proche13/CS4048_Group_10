package com.example.zerovelocity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

// This fragment is the log entry screen.
// The user picks a category (Drink, Cigarette or Vape), fills in the details
// and takes or picks a photo before submitting. The photo is uploaded to
// Firebase Storage and the rest of the data goes to the Realtime Database
// through LogDrinkViewModel and LogRepo.
public class LogDrinkFragment extends Fragment {

    // UI views
    private MaterialButtonToggleGroup toggleCategory;
    private TextInputLayout tilItemName, tilUnits;  // tilItemName label changes depending on category
    private AutoCompleteTextView etItemName;         // shows past entries as suggestions while typing
    private TextInputEditText etUnits, etLocation, etDescription;
    private ImageView ivPreview;                     // shows a preview of the selected photo
    private View llPhotoPlaceholder;                 // the camera icon shown before a photo is picked
    private MaterialButton btnLog;

    private LogDrinkViewModel viewModel;
    private ArrayAdapter<String> suggestionAdapter;  // feeds past item names into the autocomplete field

    // keeps track of which category the user currently has selected
    private LogEntry.Category currentCategory = LogEntry.Category.Drink;

    // the URI of the photo the user has selected, either from gallery or camera
    private Uri selectedImageUri;

    // we need to store the camera URI separately so we can access it in the camera result callback
    private Uri cameraImageUri;

    // activity result launchers have to be registered in onCreate, not onCreateView
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

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
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log_drink, container, false);

        // grab all the views from the layout
        toggleCategory     = view.findViewById(R.id.toggle_category);
        tilItemName        = view.findViewById(R.id.til_item_name);
        tilUnits           = view.findViewById(R.id.til_units);
        etItemName         = view.findViewById(R.id.et_item_name);
        etUnits            = view.findViewById(R.id.et_units);
        etLocation         = view.findViewById(R.id.et_location);
        etDescription      = view.findViewById(R.id.et_description);
        ivPreview          = view.findViewById(R.id.iv_preview);
        llPhotoPlaceholder = view.findViewById(R.id.ll_photo_placeholder);
        btnLog             = view.findViewById(R.id.btn_log);

        // set up the autocomplete adapter with an empty list for now
        // the list gets filled in when we load suggestions from Firebase below
        suggestionAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        etItemName.setAdapter(suggestionAdapter);

        // set up the view model and start observing suggestions
        // when the suggestions LiveData updates we swap out the adapter contents
        viewModel = new ViewModelProvider(this).get(LogDrinkViewModel.class);
        viewModel.getSuggestions().observe(getViewLifecycleOwner(), items -> {
            suggestionAdapter.clear();
            suggestionAdapter.addAll(items);
        });

        // load suggestions for the default category (Drink) on first open
        viewModel.loadSuggestions(currentCategory);

        // when the user switches category we update the field hints and reload suggestions
        // for example switching to Vape changes "Drink Name" to "Flavour"
        toggleCategory.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_log_drink)           currentCategory = LogEntry.Category.Drink;
            else if (checkedId == R.id.btn_log_cigarette)  currentCategory = LogEntry.Category.Cigarette;
            else                                           currentCategory = LogEntry.Category.Vape;
            updateHintsForCategory();
            viewModel.loadSuggestions(currentCategory);
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

    // updates the field labels to match the selected category
    // also clears the item name field since the old value is no longer relevant
    private void updateHintsForCategory() {
        switch (currentCategory) {
            case Drink:
                tilItemName.setHint("Drink Name");
                tilUnits.setHint("Units (standard drinks)");
                break;
            case Cigarette:
                tilItemName.setHint("Brand");
                tilUnits.setHint("Number of cigarettes");
                break;
            case Vape:
                tilItemName.setHint("Flavour");
                tilUnits.setHint("Puffs / sessions");
                break;
        }
        etItemName.setText("");
        tilItemName.setError(null);
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
        String itemName    = etItemName.getText()    != null ? etItemName.getText().toString().trim()    : "";
        String unitsStr    = etUnits.getText()       != null ? etUnits.getText().toString().trim()       : "";
        String location    = etLocation.getText()    != null ? etLocation.getText().toString().trim()    : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        // item name is required, the hint text tells the user what to enter
        if (itemName.isEmpty()) {
            tilItemName.setError(tilItemName.getHint() + " is required");
            return;
        }
        tilItemName.setError(null);

        // units field is required and must be a valid number
        if (unitsStr.isEmpty()) {
            tilUnits.setError("Required");
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

        // step 1: upload the photo to Firebase Storage
        // step 2: once uploaded, get the public download URL
        // step 3: save the log entry to the database with that URL attached
        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(snap ->
                        snap.getStorage().getDownloadUrl()
                                .addOnSuccessListener(downloadUri -> {
                                    viewModel.logEvent(currentCategory, finalItemName, finalUnits,
                                            finalDesc, finalLocation, downloadUri.toString());
                                    Toast.makeText(requireContext(), "Logged!", Toast.LENGTH_SHORT).show();
                                    clearForm();
                                })
                                .addOnFailureListener(this::onUploadError))
                .addOnFailureListener(this::onUploadError);
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
        etLocation.setText("");
        etDescription.setText("");
        selectedImageUri = null;
        ivPreview.setVisibility(View.GONE);
        llPhotoPlaceholder.setVisibility(View.VISIBLE);
        setBusy(false);
    }
}