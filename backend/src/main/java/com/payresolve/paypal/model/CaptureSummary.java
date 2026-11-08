package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of a capture transaction within an Order's purchase unit.
 */
public record CaptureSummary(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("amount") Amount amount,
    @JsonProperty("final_capture") Boolean finalCapture,
    @JsonProperty("seller_receivable_breakdown") SellerReceivableBreakdown sellerReceivableBreakdown,
    @JsonProperty("create_time") String createTime,
    @JsonProperty("update_time") String updateTime
) {
}
