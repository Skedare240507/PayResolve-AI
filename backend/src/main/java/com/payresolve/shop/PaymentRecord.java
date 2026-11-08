package com.payresolve.shop;

import com.payresolve.common.Money;

import java.time.Instant;

/**
 * Domain record representing a payment capture attached to a shop order.
 */
public record PaymentRecord(
    String id,
    String shopOrderId,
    String paypalCaptureId,
    long amountMinor,
    String currency,
    String status,
    Instant capturedAt
) {
    public Money amount() {
        return Money.of(amountMinor, currency);
    }
}

