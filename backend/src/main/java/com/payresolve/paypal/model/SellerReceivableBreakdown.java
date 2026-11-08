package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Breakdown of seller receivable amounts including gross, fee, and net.
 */
public record SellerReceivableBreakdown(
    @JsonProperty("gross_amount") Amount grossAmount,
    @JsonProperty("paypal_fee") Amount paypalFee,
    @JsonProperty("net_amount") Amount netAmount
) {
}
