package com.payresolve.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Single ordered step in the end-to-end payment trace timeline for a shop order.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderTraceStepDto(
    String id,
    String label,
    String status,
    boolean completed,
    Instant timestamp,
    String timestampLabel,
    String detail,
    String externalRef,
    String explanation
) {
}

