package com.payresolve.cases;

/**
 * Case lifecycle states:
 * OPEN -> INVESTIGATING -> ACTION_PROPOSED -> RESOLVED / DISMISSED
 */
public enum CaseStatus {
    OPEN("Open"),
    INVESTIGATING("Investigating"),
    ACTION_PROPOSED("Proposed"),
    RESOLVED("Resolved"),
    DISMISSED("Dismissed");

    private final String displayName;

    CaseStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == DISMISSED;
    }

    public static CaseStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CaseStatus status : values()) {
            if (status.name().equalsIgnoreCase(value) || status.displayName.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown case status: " + value);
    }
}
