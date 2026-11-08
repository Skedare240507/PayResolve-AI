package com.payresolve.cases;

/**
 * Priority and urgency rating for exception cases.
 */
public enum CaseSeverity {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    private final String displayName;

    CaseSeverity(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static CaseSeverity fromString(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        for (CaseSeverity severity : values()) {
            if (severity.name().equalsIgnoreCase(value) || severity.displayName.equalsIgnoreCase(value)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("Unknown case severity: " + value);
    }
}
