package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Purchase unit representing a merchant order item group inside a PayPal Order.
 */
public record PurchaseUnit(
    @JsonProperty("reference_id") String referenceId,
    @JsonProperty("custom_id") String customId,
    @JsonProperty("amount") Amount amount,
    @JsonProperty("payments") PaymentCollection payments
) {
}
