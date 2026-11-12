package com.payresolve.shop.dto;

import java.util.List;

/**
 * Minimal buyer-facing order status response returned by the public PayPal return endpoints
 * without exposing merchant customer labels or internal operator data.
 */
public record BuyerOrderStatusDto(
    String status,
    long amountMinor,
    String currency,
    String amount,
    List<OrderTraceStepDto> steps
) {}

