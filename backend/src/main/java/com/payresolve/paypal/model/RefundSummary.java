package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of a refund transaction within an Order's purchase unit.
 */
public record RefundSummary(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("amount") Amount amount,
    @JsonProperty("note_to_payer") String noteToPayer,
    @JsonProperty("create_time") String createTime,
    @JsonProperty("update_time") String updateTime
) {
}
