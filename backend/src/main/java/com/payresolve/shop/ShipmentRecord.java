package com.payresolve.shop;

import java.time.Instant;

/**
 * Domain entity representing a fulfillment shipment for a merchant order.
 */
public record ShipmentRecord(
    String id,
    String shopOrderId,
    String carrier,
    String trackingRef,
    String status,
    Instant deliveredAt,
    Instant createdAt
) {
    public boolean isDelivered() {
        return "DELIVERED".equalsIgnoreCase(status);
    }
}
