package com.payresolve.evidence;

/**
 * Directed relationship types connecting nodes in the Evidence Graph.
 */
public enum EvidenceEdgeType {
    CAPTURES("captures"),
    NOTIFIED_BY("notified_by"),
    FULFILLS("fulfills"),
    REFUNDS("refunds"),
    DISPUTES("disputes"),
    PROVES("proves"),
    ACTED_ON("acted_on");

    private final String relation;

    EvidenceEdgeType(String relation) {
        this.relation = relation;
    }

    public String relation() {
        return relation;
    }
}

