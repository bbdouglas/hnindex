package com.bbdouglas.hnindex;

public class Event {

    private final String type;
    private final String data;
    private final String eventId;

    public Event(String type, String data, String eventId) {
        this.type = type;
        this.data = data;
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public String getEventId() {
        return eventId;
    }
}
