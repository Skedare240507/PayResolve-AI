package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.payresolve.shop.Product;

/**
 * Data transfer object for demo store products.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
    String id,
    String name,
    long priceMinor,
    String currency,
    double price,
    String description
) {
    public static ProductDto from(Product product) {
        return new ProductDto(
            product.id(),
            product.name(),
            product.priceMinor(),
            product.currency(),
            product.price().asMajorUnits(),
            product.description()
        );
    }
}
