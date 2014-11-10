package com.bbdouglas.hnindex;

public class EventBuilder {
    private String type;
    private StringBuilder dataBuffer;
    private String lastEventId;

    public EventBuilder() {
        reset();
        lastEventId = "";
    }

    public void reset() {
        type = "";
        dataBuffer = new StringBuilder();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public StringBuilder getDataBuffer() {
        return dataBuffer;
    }

    public void appendData(String data) {
        dataBuffer.append(data);
        dataBuffer.append('\n');
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }
}
