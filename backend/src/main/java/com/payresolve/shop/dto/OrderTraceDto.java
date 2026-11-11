package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Full payment lifecycle trace for a shop order, showing every step from order
 * creation through PayPal checkout, capture, webhook delivery, and case creation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderTraceDto(
    String orderId,
    String paypalOrderId,
    String orderStatus,
    long totalMinor,
    String currency,
    String captureId,
    String linkedCaseId,
    Instant generatedAt,
    List<OrderTraceStepDto> steps
) {
}

