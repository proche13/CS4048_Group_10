package com.example.zerovelocity;

import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.auth.User;

public class LogDrinkViewModel extends ViewModel {
    private final LogRepo repo = LogRepo.getInstance();

    public void logEvent(LogEntry.Category category, String name, String type, float units){
        User currentUser = UserSession.currentUser;

        if(currentUser == null){
            return;
        }
        repo.logEvent(currentUser.getId(), currentUser.getUsername(), category, name, type, units);
    }
}
