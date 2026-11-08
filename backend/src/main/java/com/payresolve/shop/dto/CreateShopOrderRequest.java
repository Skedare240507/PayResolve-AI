package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Request payload to create a new order in the demo shop.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateShopOrderRequest(
    List<OrderItemRequest> items,
    String customerLabel,
    String currency
) {}
