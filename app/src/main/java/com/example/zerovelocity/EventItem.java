package com.example.zerovelocity;

public class EventItem {
    public String id;
    public String title;
    public long date;
    public long startTime;
    public long endTime;
    public String description;
    public String locationLabel;
    public double latitude;
    public double longitude;
    public String imageUrl;
    public String createdBy;
    public String inviteStatus; // "pending", "accepted", "declined"
    public boolean canInvite;
    public int attendeeCount;

    public EventItem() {}

    public EventItem(String id, String title, long date, long startTime, long endTime) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
