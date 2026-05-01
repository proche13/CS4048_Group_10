package com.example.zerovelocity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LogRepo {

    public interface OnSuggestionsLoaded {
        void onLoaded(List<String> items);
    }

    private static LogRepo instance;
    private final DatabaseReference logsRef;
    private final DatabaseReference usersRef;
    private final DatabaseReference prefsRef;

    private LogRepo() {
        DatabaseReference root = FirebaseRefs.root();
        logsRef  = root.child("consumptionLogs");
        usersRef = root.child("users");
        prefsRef = root.child("userPreferences");
    }

    public static LogRepo getInstance() {
        if (instance == null) instance = new LogRepo();
        return instance;
    }

    // Saves a full consumption log entry to Firebase
    public void logEvent(String userID, String username, LogEntry.Category category,
                         String itemName, float units,
                         String description, String location,
                         Double latitude, Double longitude, String imageUrl) {

        String eventId = UUID.randomUUID().toString();

        Map<String, Object> entry = new HashMap<>();
        entry.put("eventId",     eventId);
        entry.put("userID",      userID);
        entry.put("username",    username);
        entry.put("category",    category.name());
        entry.put("itemName",    itemName);
        entry.put("units",       units);
        entry.put("description", description);
        entry.put("location",    location);
        if (latitude != null && longitude != null) {
            entry.put("latitude", latitude);
            entry.put("longitude", longitude);
        }
        entry.put("imageUrl",    imageUrl);
        entry.put("timestamp",   System.currentTimeMillis());

        logsRef.child(eventId).setValue(entry)
                .addOnSuccessListener(a -> {
                    android.util.Log.d("LogRepo", "Event logged: " + eventId);
                    incrementUserCounter(userID, category, units);
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("LogRepo", "Failed to log: " + e.getMessage()));
    }

    // Increments the per-category running total on the user profile
    private void incrementUserCounter(String userID, LogEntry.Category category, float units) {
        switch (category) {
            case Drink:
                usersRef.child(userID).child("totalDrinkUnits")
                        .setValue(ServerValue.increment(units));
                break;
            case Cigarette:
                usersRef.child(userID).child("totalCigarettes")
                        .setValue(ServerValue.increment(units));
                break;
        }
    }

    // Saves an item name suggestion under the user's preferences for later autocomplete
    public void saveItemSuggestion(String userID, LogEntry.Category category, String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) return;
        prefsRef.child(userID).child("suggestions").child(category.name())
                .push().setValue(itemName.trim());
    }

    // Loads previously saved item name suggestions for a given category
    public void loadSuggestions(String userID, LogEntry.Category category,
                                OnSuggestionsLoaded callback) {
        prefsRef.child(userID).child("suggestions").child(category.name())
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> seen = new LinkedHashSet<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String val = child.getValue(String.class);
                        if (val != null) seen.add(val);
                    }
                    callback.onLoaded(new ArrayList<>(seen));
                })
                .addOnFailureListener(e -> callback.onLoaded(new ArrayList<>()));
    }
}
