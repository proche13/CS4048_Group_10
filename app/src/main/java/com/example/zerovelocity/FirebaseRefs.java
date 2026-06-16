package com.example.zerovelocity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public final class FirebaseRefs {

    private static final String DATABASE_URL =
            "https://mostpolluted-default-rtdb.europe-west1.firebasedatabase.app/";

    private FirebaseRefs() {
    }

    public static FirebaseDatabase database() {
        return FirebaseDatabase.getInstance(DATABASE_URL);
    }

    public static DatabaseReference root() {
        return database().getReference();
    }
}
