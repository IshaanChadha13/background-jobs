package com.example.capstone.background_jobs.model;

public enum AlertStateBg {

    OPEN,
    FALSE_POSITIVE,
    SUPPRESSED,
    FIXED,
    CONFIRM;

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
                if (reason.contains("false positive") || reason.contains("false_positive") || reason.contains("inaccurate")) {
                    return FALSE_POSITIVE;
                } else {
                    return SUPPRESSED;
                }
            }
            // fallback if unknown => SUPPRESSED
            return SUPPRESSED;
        }

        if("false positive".equals(lowerState) || "false_positive".equals(lowerState)) {
            return FALSE_POSITIVE;
        }
        // fallback
        return OPEN;
    }
}