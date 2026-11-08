package com.payresolve.shop;

import com.payresolve.common.Money;

import java.time.Instant;

/**
 * Domain entity representing a refund transaction.
 */
public record RefundRecord(
    String id,
    String paymentId,
    String paypalRefundId,
    long amountMinor,
    String currency,
    String status,
    String source,
    Instant createdAt
) {
    public Money amount() {
        return Money.of(amountMinor, currency);
    }
}
