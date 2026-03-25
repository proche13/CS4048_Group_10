package com.example.zerovelocity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LogRepo {
    private static LogRepo instance;

    private LogRepo() {
    }

    private static LogRepo getInstance(){
        if(instance == null){
            instance = new LogRepo();
        }
        return instance;
    }
/*
  public void logEvent(String eventId, String userID, String username, String type, String name, float units){
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
  }*/
}
