package com.payresolve.shop;

/**
 * Domain record representing an item line belonging to a shop order.
 */
public record ShopOrderItem(
    Long id,
    String shopOrderId,
    String productId,
    int quantity,
    long unitPriceMinor
) {}

