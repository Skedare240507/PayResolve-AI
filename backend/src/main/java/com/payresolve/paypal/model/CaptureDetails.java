package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Detailed capture information returned by GET /v2/payments/captures/{id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptureDetails(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("amount") Amount amount,
    @JsonProperty("final_capture") Boolean finalCapture,
    @JsonProperty("custom_id") String customId,
    @JsonProperty("seller_receivable_breakdown") SellerReceivableBreakdown sellerReceivableBreakdown,
    @JsonProperty("create_time") String createTime,
    @JsonProperty("update_time") String updateTime,
    @JsonProperty("links") List<LinkDescription> links
) {
    public CaptureDetails {
        if (links == null) {
            links = Collections.emptyList();
        }
    }
}
