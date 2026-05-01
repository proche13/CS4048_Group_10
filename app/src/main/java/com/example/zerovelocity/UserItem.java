package com.example.zerovelocity;

public class UserItem {
    public final String uid;
    public final String displayName;
    public final boolean pending;

    public UserItem(String uid, String displayName) {
        this(uid, displayName, false);
    }

    public UserItem(String uid, String displayName, boolean pending) {
        this.uid = uid;
        this.displayName = displayName;
        this.pending = pending;
    }
}
