package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.payresolve.shop.ShipmentRecord;

import java.time.Instant;

/**
 * Presentation DTO for simulated merchant shipments.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentResponseDto(
    String id,
    String shopOrderId,
    String carrier,
    String trackingRef,
    String status,
    Instant deliveredAt,
    Instant createdAt
) {
    public static ShipmentResponseDto from(ShipmentRecord record) {
        return new ShipmentResponseDto(
            record.id(),
            record.shopOrderId(),
            record.carrier(),
            record.trackingRef(),
            record.status(),
            record.deliveredAt(),
            record.createdAt()
        );
    }
}
