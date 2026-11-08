package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.payresolve.shop.ShopOrderItem;

/**
 * Presentation DTO for an item line in a shop order.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopOrderItemDto(
    Long id,
    String productId,
    int quantity,
    long unitPriceMinor
) {
    public static ShopOrderItemDto from(ShopOrderItem item) {
        return new ShopOrderItemDto(
            item.id(),
            item.productId(),
            item.quantity(),
            item.unitPriceMinor()
        );
    }
}

