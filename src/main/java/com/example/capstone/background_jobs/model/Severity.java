package com.example.capstone.background_jobs.model;

public enum Severity {

    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFORMATIONAL;

    public static Severity fromRaw(String raw) {

        if (raw == null || raw.isEmpty()) {
            return MEDIUM;
        }
        // Convert to lowercase so comparisons are easier
        raw = raw.toLowerCase();

        switch (raw) {
            case "critical":
            case "severe":
                return CRITICAL;
            case "high":
            case "important":
                return HIGH;
            case "medium":
            case "moderate":
                return MEDIUM;
            case "low":
            case "minor":
                return LOW;
            case "info":
            case "informational":
            case "notice":
                return INFORMATIONAL;
            case "error": // e.g. code scanning "error"
                return HIGH;
            default:
                return MEDIUM;
        }
    }
}
