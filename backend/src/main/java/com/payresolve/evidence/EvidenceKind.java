package com.payresolve.evidence;

/**
 * Types of evidence nodes participating in the case evidence graph.
 */
public enum EvidenceKind {
    PAYPAL_ORDER("PayPal Order"),
    PAYPAL_CAPTURE("PayPal Capture"),
    PAYPAL_REFUND("PayPal Refund"),
    WEBHOOK_EVENT("Webhook Event"),
    SHOP_ORDER("Shop Order"),
    SHIPMENT("Shipment"),
    DISPUTE("Dispute"),
    DISPUTE_EVIDENCE("Dispute Evidence"),
    OPERATOR_ACTION("Operator Action");

    private final String displayName;

    EvidenceKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static EvidenceKind fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (EvidenceKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value) || kind.displayName.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown evidence kind: " + value);
    }
}

