package com.payresolve.shop;

import com.payresolve.common.Money;

import java.time.Instant;

/**
 * Domain record representing an order in the demo merchant shop.
 */
public record ShopOrder(
    String id,
    String paypalOrderId,
    String status,
    long totalMinor,
    String currency,
    String customerLabel,
    Instant createdAt,
    Instant updatedAt
) {
    public Money total() {
        return Money.of(totalMinor, currency);
    }

    public boolean isPaid() {
        return "PAID".equalsIgnoreCase(status);
    }

    public boolean isAwaitingConfirmation() {
        return "AWAITING_CONFIRMATION".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }
}
