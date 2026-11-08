package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Result of initiating a PayPal Sandbox checkout for a merchant order.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutResponseDto(
    String shopOrderId,
    String paypalOrderId,
    String approveUrl,
    String status,
    long totalMinor,
    String currency
) {}
