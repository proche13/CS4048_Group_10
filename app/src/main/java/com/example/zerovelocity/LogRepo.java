package com.example.zerovelocity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LogRepo {
    private static LogRepo instance;
    private final DatabaseReference dbRef;

    private LogRepo() {
        // points to the logs node in the Realtime Database
        dbRef = FirebaseDatabase.getInstance("https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference("consumptionLogs");
    }

    public static LogRepo getInstance() {
        if (instance == null) {
            instance = new LogRepo();
        }
        return instance;
    }

    public void logEvent(String userID, String username, String type, String name,
                         float units, LogEntry.Category category) {

        String eventId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        // store as a plain map so Realtime Database can serialise it cleanly
        Map<String, Object> entry = new HashMap<>();
        entry.put("eventId",    eventId);
        entry.put("userID",     userID);
        entry.put("username",   username);
        entry.put("category",   category.name());   // store enum as String
        entry.put("type",       type);
        entry.put("name",       name);
        entry.put("units",      units);
        entry.put("timestamp",  timestamp);

        // saves to logs/{eventId} in the Realtime Database
        dbRef.child(eventId).setValue(entry)
                .addOnSuccessListener(a ->
                        android.util.Log.d("LogRepo", "Drink logged successfully"))
                .addOnFailureListener(e ->
                        android.util.Log.e("LogRepo", "Failed to log drink: " + e.getMessage()));
    }
}
