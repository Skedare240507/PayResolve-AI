package com.payresolve.shop;

import com.payresolve.common.Money;

import java.time.Instant;

/**
 * Domain entity representing a product catalog item in the demo store.
 */
public record Product(
    String id,
    String name,
    long priceMinor,
    String currency,
    String description,
    Instant createdAt
) {
    public Money price() {
        return Money.of(priceMinor, currency);
    }
}
