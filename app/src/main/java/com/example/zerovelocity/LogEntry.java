package com.example.zerovelocity;

public class LogEntry {

    public enum Type {
        CIGARETTE("Cigarette"),
        VAPE("Vape"),
        DRINK("Drink");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final long id;
    private final Type type;
    private final int quantity;
    private final long timestampMillis;

    public LogEntry(Type type, int quantity, long timestampMillis){
        this.id = System.nanoTime();
        this.type = type;
        this.quantity = quantity;
        this.timestampMillis = timestampMillis;
    }

    public long getId(){
        return id;
    }

    public Type getType(){
        return type;
    }

    public int getQuantity(){
        return quantity;
    }

    public long getTimestampMillis(){
        return timestampMillis;
    }
}