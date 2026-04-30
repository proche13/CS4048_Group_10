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

public class LogDrinkFragment extends Fragment {

    private MaterialButtonToggleGroup toggleCategory;
    private TextInputLayout tilItemName, tilUnits;
    private AutoCompleteTextView etItemName;
    private TextInputEditText etUnits, etLocation, etDescription;
    private ImageView ivPreview;
    private View llPhotoPlaceholder;
    private MaterialButton btnLog;

    private LogDrinkViewModel viewModel;
    private ArrayAdapter<String> suggestionAdapter;

    private LogEntry.Category currentCategory = LogEntry.Category.Drink;
    private Uri selectedImageUri;
    private Uri cameraImageUri;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) setImagePreview(uri); });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> { if (success && cameraImageUri != null) setImagePreview(cameraImageUri); });

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) openCamera(); else
                    Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show(); });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log_drink, container, false);

        toggleCategory   = view.findViewById(R.id.toggle_category);
        tilItemName      = view.findViewById(R.id.til_item_name);
        tilUnits         = view.findViewById(R.id.til_units);
        etItemName       = view.findViewById(R.id.et_item_name);
        etUnits          = view.findViewById(R.id.et_units);
        etLocation       = view.findViewById(R.id.et_location);
        etDescription    = view.findViewById(R.id.et_description);
        ivPreview        = view.findViewById(R.id.iv_preview);
        llPhotoPlaceholder = view.findViewById(R.id.ll_photo_placeholder);
        btnLog           = view.findViewById(R.id.btn_log);

        // Autocomplete adapter — populated from Firebase suggestions
        suggestionAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        etItemName.setAdapter(suggestionAdapter);

        viewModel = new ViewModelProvider(this).get(LogDrinkViewModel.class);
        viewModel.getSuggestions().observe(getViewLifecycleOwner(), items -> {
            suggestionAdapter.clear();
            suggestionAdapter.addAll(items);
        });
        viewModel.loadSuggestions(currentCategory);

        // Category toggle
        toggleCategory.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_log_drink)      currentCategory = LogEntry.Category.Drink;
            else if (checkedId == R.id.btn_log_cigarette) currentCategory = LogEntry.Category.Cigarette;
            else                                       currentCategory = LogEntry.Category.Vape;
            updateHintsForCategory();
            viewModel.loadSuggestions(currentCategory);
        });

        view.findViewById(R.id.btn_gallery).setOnClickListener(v ->
                galleryLauncher.launch("image/*"));

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

    private void setImagePreview(Uri uri) {
        selectedImageUri = uri;
        ivPreview.setImageURI(uri);
        ivPreview.setVisibility(View.VISIBLE);
        llPhotoPlaceholder.setVisibility(View.GONE);
    }

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

    private void submit() {
        String itemName   = etItemName.getText() != null ? etItemName.getText().toString().trim() : "";
        String unitsStr   = etUnits.getText() != null ? etUnits.getText().toString().trim() : "";
        String location   = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        // Validate item name
        if (itemName.isEmpty()) {
            tilItemName.setError(tilItemName.getHint() + " is required");
            return;
        }
        tilItemName.setError(null);

        // Validate units
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

        // Validate image
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Please add a photo", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);

        String uid     = viewModel.getCurrentUid();
        String eventId = UUID.randomUUID().toString();

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference("logs/" + uid + "/" + eventId + ".jpg");

        final float finalUnits       = units;
        final String finalItemName   = itemName;
        final String finalLocation   = location;
        final String finalDesc       = description;

        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(snap ->
                        snap.getStorage().getDownloadUrl()
                                .addOnSuccessListener(downloadUri -> {
                                    viewModel.logEvent(currentCategory, finalItemName, finalUnits,
                                            finalDesc, finalLocation, downloadUri.toString());
                                    Toast.makeText(requireContext(), "Logged!", Toast.LENGTH_SHORT).show();
                                    clearForm();
                                })
                                .addOnFailureListener(e -> onUploadError()))
                .addOnFailureListener(e -> onUploadError());
    }

    private void onUploadError() {
        setBusy(false);
        Toast.makeText(requireContext(), "Photo upload failed — please try again", Toast.LENGTH_SHORT).show();
    }

    private void setBusy(boolean busy) {
        btnLog.setEnabled(!busy);
        btnLog.setText(busy ? "Uploading..." : "Log Entry");
    }

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