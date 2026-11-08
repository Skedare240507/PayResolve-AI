package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Submitted evidence items within a dispute.
 */
public record DisputeEvidence(
    @JsonProperty("evidence_type") String evidenceType,
    @JsonProperty("notes") String notes
) {
}
