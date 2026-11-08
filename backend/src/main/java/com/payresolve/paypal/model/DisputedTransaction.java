package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Details of transactions involved in a dispute.
 */
public record DisputedTransaction(
    @JsonProperty("buyer_transaction_id") String buyerTransactionId,
    @JsonProperty("seller_transaction_id") String sellerTransactionId,
    @JsonProperty("gross_amount") Amount grossAmount
) {
}
