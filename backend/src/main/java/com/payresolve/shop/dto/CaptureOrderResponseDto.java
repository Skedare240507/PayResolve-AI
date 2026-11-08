package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Result of capturing a PayPal order server-side.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptureOrderResponseDto(
    String shopOrderId,
    String paypalOrderId,
    String captureId,
    String status,
    long amountCapturedMinor,
    String currency,
    String message
) {}
