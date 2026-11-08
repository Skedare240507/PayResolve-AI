package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.payresolve.shop.ShopOrder;

import java.time.Instant;
import java.util.List;

/**
 * Presentation DTO for merchant shop orders.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopOrderDto(
    String id,
    String paypalOrderId,
    String status,
    long totalMinor,
    String currency,
    double total,
    String customerLabel,
    List<ShopOrderItemDto> items,
    Instant createdAt,
    Instant updatedAt
) {
    public static ShopOrderDto from(ShopOrder order) {
        return from(order, List.of());
    }

    public static ShopOrderDto from(ShopOrder order, List<ShopOrderItemDto> items) {
        return new ShopOrderDto(
            order.id(),
            order.paypalOrderId(),
            order.status(),
            order.totalMinor(),
            order.currency(),
            order.total().asMajorUnits(),
            order.customerLabel(),
            items != null ? items : List.of(),
            order.createdAt(),
            order.updatedAt()
        );
    }
}
