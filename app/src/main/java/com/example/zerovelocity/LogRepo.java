package com.example.zerovelocity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

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

  public void logEvent(String eventId, String userID, String username, String type, String name, float units){
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
  }
}
