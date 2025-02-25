package com.example.capstone.background_jobs.model;

public interface Acknowledgement<T> {

    String getAcknowledgementId();
    T getPayload();
}
