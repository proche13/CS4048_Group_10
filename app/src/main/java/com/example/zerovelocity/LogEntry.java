package com.example.zerovelocity;

public class LogEntry {

    public enum Category {
        Drink,
        Cigarette,
        Vape
    }

    private final String eventId;
    private final String userID;
    private final String username;
    private final Category category;
    private final String itemName;   // drink name / cigarette brand / vape flavour
    private final float units;
    private final String description;
    private final String location;
    private final String imageUrl;
    private final long timestampMillis;

    public LogEntry(String eventId, String userID, String username, Category category,
                    String itemName, float units, String description, String location,
                    String imageUrl, long timestampMillis) {
        this.eventId = eventId;
        this.userID = userID;
        this.username = username;
        this.category = category;
        this.itemName = itemName;
        this.units = units;
        this.description = description;
        this.location = location;
        this.imageUrl = imageUrl;
        this.timestampMillis = timestampMillis;
    }

    public String getEventId()       { return eventId; }
    public String getUserID()        { return userID; }
    public String getUsername()      { return username; }
    public Category getCategory()    { return category; }
    public String getItemName()      { return itemName; }
    public float getUnits()          { return units; }
    public String getDescription()   { return description; }
    public String getLocation()      { return location; }
    public String getImageUrl()      { return imageUrl; }
    public long getTimestampMillis() { return timestampMillis; }
}