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
    private final String type;

    private final String name;
    private final float units;
    private final long timestampMillis;

    public LogEntry(String eventId, String userID, String username, Category category, float units, long timestampMillis, String type, String name){
        this.eventId = eventId;
        this.userID = userID;
        this.username = username;
        this.category = category;
        this.type = type;
        this.name = name;
        this.units = units;
        this.timestampMillis = timestampMillis;
    }

    public String getEventId(){
        return eventId;
    }

    public String getUserID(){
        return userID;
    }

    public String getUsername(){
        return username;
    }

    public String getType(){
        return type;
    }

    public String getName(){
        return name;
    }

    public Category getCategory(){
        return category;
    }

    public float getUnits() {
        return units;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}