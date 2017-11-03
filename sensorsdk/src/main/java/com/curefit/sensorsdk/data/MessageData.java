package com.curefit.sensorsdk.data;

import com.google.firebase.database.PropertyName;

/**
 * Created by rahul on 22/09/17.
 */

public class MessageData {
    @PropertyName("m")
    public String message;
    @PropertyName("ts")
    public long timestamp;
    public MessageData(String message, long timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }
}