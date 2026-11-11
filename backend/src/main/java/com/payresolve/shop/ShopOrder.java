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
    Instant updatedAt,
    Instant paypalOrderCreatedAt,
    Instant buyerApprovedAt,
    Instant capturedAt,
    String paypalCaptureId,
    Instant paidAt
) {
    /** Backwards-compatible 8-arg constructor for unit tests. */
    public ShopOrder(
        String id,
        String paypalOrderId,
        String status,
        long totalMinor,
        String currency,
        String customerLabel,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, paypalOrderId, status, totalMinor, currency, customerLabel, createdAt, updatedAt, null, null, null, null, null);
    }
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
