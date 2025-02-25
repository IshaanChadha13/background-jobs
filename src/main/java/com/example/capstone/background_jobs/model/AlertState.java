package com.example.capstone.background_jobs.model;

public enum AlertState {

    OPEN,
    FALSE_POSITIVE,
    SUPPRESSED,
    FIXED,
    CONFIRM;

    /**
     * Takes raw string (e.g. "open", "false_positive", "resolved"),
     * normalizes, and returns the matching enum. Defaults to OPEN if unknown.
     */
    public static AlertState fromRaw(String raw) {
        if (raw == null || raw.isEmpty()) {
            return OPEN;
        }
        raw = raw.toLowerCase().replace("_", " ");

        switch (raw) {
            case "dismissed":
                return SUPPRESSED;
//                return FALSE_POSITIVE;
            case "open":
            case "new":
                return OPEN;
            case "false positive":
            case "false_positive":
            case "unlikely":
                return FALSE_POSITIVE;
            case "suppressed":
            case "ignored":
                return SUPPRESSED;
            case "fixed":
            case "resolved":
                return FIXED;
            case "confirm":
            case "acknowledged":
                return CONFIRM;
            default:
                return OPEN;
        }
    }

    public static AlertState fromRaw(String rawState, String toolType, String dismissedReason) {
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

        // "dismissed" logic => we interpret the reason based on the tool
        if ("dismissed".equals(lowerState)) {
            String type = (toolType == null) ? "" : toolType.toUpperCase();
            String reason = (dismissedReason == null) ? "" : dismissedReason.toLowerCase();

            switch (type) {
                case "CODE_SCANNING":
                case "SECRET_SCANNING":
                    // E.g. code scanning might pass "false positive", "used in tests", or "won't fix"
                    // If reason includes "false positive" or "inaccurate", => FALSE_POSITIVE
                    // else => SUPPRESSED
                    if (reason.contains("false positive") || reason.contains("inaccurate")) {
                        return FALSE_POSITIVE;
                    } else {
                        return SUPPRESSED;
                    }

                case "DEPENDABOT":
                    // Dependabot might pass "inaccurate", "fix_started", "no_bandwidth", ...
                    // If reason is "inaccurate", => FALSE_POSITIVE
                    // else => SUPPRESSED
                    if (reason.contains("inaccurate") || reason.contains("false positive")) {
                        return FALSE_POSITIVE;
                    } else {
                        return SUPPRESSED;
                    }

                default:
                    // fallback if unknown tool => SUPPRESSED
                    return SUPPRESSED;
            }
        }

        // fallback
        return OPEN;
    }

}
