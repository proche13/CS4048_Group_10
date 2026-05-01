package com.example.zerovelocity;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class LogDrinkViewModel extends ViewModel {

    private final LogRepo repo = LogRepo.getInstance();
    private final MutableLiveData<List<String>> suggestions =
            new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<String>> getSuggestions() {
        return suggestions;
    }

    public String getCurrentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // Saves the log entry and records the item name as a suggestion for next time
    public void logEvent(LogEntry.Category category, String itemName, float units,
                         String description, String location,
                         Double latitude, Double longitude, String imageUrl) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userID   = currentUser.getUid();
        String username = currentUser.getDisplayName() != null
                ? currentUser.getDisplayName() : "Unknown";

        repo.logEvent(userID, username, category, itemName, units, description, location,
                latitude, longitude, imageUrl);

        if (!itemName.isEmpty()) {
            repo.saveItemSuggestion(userID, category, itemName);
        }
    }

    // Loads previously used item names for autocomplete suggestions
    public void loadSuggestions(LogEntry.Category category) {
        String uid = getCurrentUid();
        if (uid == null) {
            suggestions.postValue(new ArrayList<>());
            return;
        }
        repo.loadSuggestions(uid, category, list -> suggestions.postValue(list));
    }
}
