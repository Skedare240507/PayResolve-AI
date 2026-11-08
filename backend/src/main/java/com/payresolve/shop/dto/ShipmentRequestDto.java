package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request payload to create a simulated shipment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentRequestDto(
    String carrier,
    String trackingRef,
    String status
) {}
