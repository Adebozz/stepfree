package com.stepfreelondon.line;

/**
 * Our classification of TfL status severities, ordered from least to most serious so that
 * {@code max(ordinal)} gives a line's overall level.
 *
 * <p>{@link #UNKNOWN} ranks above good service on purpose: an unrecognised or missing status must
 * never be presented as "Good Service".
 */
public enum ServiceLevel {
    GOOD_SERVICE,
    INFORMATION,
    UNKNOWN,
    MINOR_DISRUPTION,
    SEVERE_DISRUPTION,
    CLOSED;

    /**
     * Maps a TfL {@code statusSeverity} code for rail modes. Source: {@code GET /Line/Meta/Severity},
     * checked 2026-10-23. Codes are documented in {@code docs/api-integration.md}.
     */
    public static ServiceLevel fromTflSeverity(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code) {
            case 10, 18 -> GOOD_SERVICE;              // Good Service, No Issues
            case 0, 19 -> INFORMATION;                // Special Service, Information
            case 7, 9, 12, 14, 15, 17 -> MINOR_DISRUPTION; // Reduced Service, Minor Delays, Exit Only, Change of frequency, Diverted, Issues Reported
            case 3, 5, 6, 8, 11, 13 -> SEVERE_DISRUPTION;  // Part Suspended, Part Closure, Severe Delays, Bus Service, Part Closed, No Step Free Access
            case 1, 2, 4, 16, 20 -> CLOSED;           // Closed, Suspended, Planned Closure, Not Running, Service Closed
            default -> UNKNOWN;
        };
    }

    public static boolean isStepFreeAccessIssue(Integer code) {
        return code != null && code == 13;
    }
}
