package com.payresolve.evidence;

/**
 * Originating system source of an evidence item.
 */
public enum EvidenceSource {
    PAYPAL("PayPal"),
    SHOP("Shop"),
    SYSTEM("System");

    private final String displayName;

    EvidenceSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static EvidenceSource fromString(String value) {
        if (value == null || value.isBlank()) {
            return SYSTEM;
        }
        for (EvidenceSource source : values()) {
            if (source.name().equalsIgnoreCase(value) || source.displayName.equalsIgnoreCase(value)) {
                return source;
            }
        }
        return SYSTEM;
    }
}

