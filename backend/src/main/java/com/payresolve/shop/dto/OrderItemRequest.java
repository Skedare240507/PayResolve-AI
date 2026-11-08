package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Line item in an order placement request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemRequest(
    String productId,
    int quantity
) {}
