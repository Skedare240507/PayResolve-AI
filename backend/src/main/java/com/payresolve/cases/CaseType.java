package com.payresolve.cases;

/**
 * Enumeration of payment discrepancy case types detected by PayResolve.
 */
public enum CaseType {
    PAID_NOT_MARKED("Paid, not marked"),
    AMOUNT_MISMATCH("Amount mismatch"),
    DUPLICATE_EVENT("Duplicate event"),
    REFUND_MISMATCH("Refund mismatch"),
    ORPHAN_CAPTURE("Orphan capture"),
    DISPUTE_EVIDENCE_GAP("Dispute evidence gap");

    private final String displayName;

    CaseType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static CaseType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CaseType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.displayName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown case type: " + value);
    }
}
