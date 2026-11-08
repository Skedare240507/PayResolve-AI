package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request payload to initiate a refund on a shop order.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundRequestDto(
    Long amountMinor,
    String reason
) {}
