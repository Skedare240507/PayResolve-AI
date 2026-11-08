package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message history within a dispute.
 */
public record DisputeMessage(
    @JsonProperty("posted_by") String postedBy,
    @JsonProperty("message") String message,
    @JsonProperty("time") String time
) {
}
