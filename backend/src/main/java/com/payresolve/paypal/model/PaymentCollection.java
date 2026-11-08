package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Collection of payments (captures, refunds) attached to an Order's purchase unit.
 */
public record PaymentCollection(
    @JsonProperty("captures") List<CaptureSummary> captures,
    @JsonProperty("refunds") List<RefundSummary> refunds
) {
    public PaymentCollection {
        if (captures == null) {
            captures = Collections.emptyList();
        }
        if (refunds == null) {
            refunds = Collections.emptyList();
        }
    }
}
