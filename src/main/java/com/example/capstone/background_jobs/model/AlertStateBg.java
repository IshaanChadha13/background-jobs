package com.example.capstone.background_jobs.model;

public enum AlertStateBg {

    OPEN,
    FALSE_POSITIVE,
    SUPPRESSED,
    FIXED,
    CONFIRM;

    /**
     * Takes a raw string (e.g. "open", "dismiss", "resolved"),
     * normalizes, and returns the matching enum. Defaults to OPEN if unknown.
     */
    public static AlertStateBg fromRaw(String rawState) {
        if (rawState == null || rawState.isEmpty()) {
            return OPEN;
        }
        String lower = rawState.toLowerCase().replace("_", " ");

        switch (lower) {
            case "dismiss":
            case "dismissed":
                return SUPPRESSED;
            case "false positive":
            case "false_positive":
            case "inaccurate":
                return FALSE_POSITIVE;
            case "fixed":
            case "resolved":
                return FIXED;
            case "confirm":
            case "acknowledged":
                return CONFIRM;
            case "open":
            case "new":
                return OPEN;
            default:
                return OPEN;
        }
    }

    /**
     * Advanced version if you also want to consider toolType and a dismissReason,
     * just like the parser does.
     */
    public static AlertStateBg fromRaw(String rawState, String toolType, String dismissedReason) {
        if (rawState == null || rawState.isEmpty()) {
            return OPEN;
        }
        String lowerState = rawState.toLowerCase().replace("_", " ");

        if ("open".equals(lowerState) || "new".equals(lowerState)) {
            return OPEN;
        }
        if ("fixed".equals(lowerState) || "resolved".equals(lowerState)) {
            return FIXED;
        }
        if ("confirm".equals(lowerState) || "acknowledged".equals(lowerState)) {
            return CONFIRM;
        }

        // Dismissed logic => interpret reason
        if ("dismiss".equals(lowerState) || "dismissed".equals(lowerState)) {
            String type = (toolType == null) ? "" : toolType.toUpperCase();
            String reason = (dismissedReason == null) ? "" : dismissedReason.toLowerCase();

            // If your code scanning / secret scanning logs "false positive" => map to FALSE_POSITIVE
            // otherwise => SUPPRESSED
            if ("CODE_SCANNING".equals(type) || "SECRET_SCANNING".equals(type) || "DEPENDABOT".equals(type)) {
                if (reason.contains("false positive") || reason.contains("inaccurate")) {
                    return FALSE_POSITIVE;
                } else {
                    return SUPPRESSED;
                }
            }
            // fallback if unknown => SUPPRESSED
            return SUPPRESSED;
        }
        // fallback
        return OPEN;
    }
}