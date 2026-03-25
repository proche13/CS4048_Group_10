package com.example.zerovelocity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.UUID;


public class LogRepo {
    private static LogRepo instance;

    private LogRepo() {
    }

    public static LogRepo getInstance(){
        if(instance == null){
            instance = new LogRepo();
        }
        return instance;
    }

  public void logEvent(String userID, String username, String type, String name, float units, LogEntry.Category category){
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String eventId = UUID.randomUUID().toString();
        long timeStamp = System.currentTimeMillis();

        LogEntry logEntry = new LogEntry(eventId, userID, username,category, units, timeStamp, type, name);

      DocumentReference ref = db.collection("logs").document(eventId);

        WriteBatch batch = db.batch();
        batch.set(ref, logEntry);
        batch.commit();
  }
}
