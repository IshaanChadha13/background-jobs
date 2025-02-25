package com.example.capstone.background_jobs.model;

public interface Event<T> {

    EventTypes getType();
    T getPayload();
    String getEventId();
}
