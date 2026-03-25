package com.example.zerovelocity;

import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LogDrinkViewModel extends ViewModel {
    private final LogRepo repo = LogRepo.getInstance();

    public void logEvent(LogEntry.Category category, String name, String type, float units){
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if(currentUser == null){
            return;
        }
        String userID = currentUser.getUid();
        String username = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Unknown";

        repo.logEvent(userID, username, name, type, units, category);
    }
}
