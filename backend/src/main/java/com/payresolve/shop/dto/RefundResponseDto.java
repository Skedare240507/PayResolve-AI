package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.payresolve.shop.RefundRecord;

import java.time.Instant;

/**
 * Presentation DTO for refund records.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundResponseDto(
    String id,
    String paymentId,
    String paypalRefundId,
    long amountMinor,
    String currency,
    double amount,
    String status,
    String source,
    Instant createdAt
) {
    public static RefundResponseDto from(RefundRecord record) {
        return new RefundResponseDto(
            record.id(),
            record.paymentId(),
            record.paypalRefundId(),
            record.amountMinor(),
            record.currency(),
            record.amount().asMajorUnits(),
            record.status(),
            record.source(),
            record.createdAt()
        );
    }
}
