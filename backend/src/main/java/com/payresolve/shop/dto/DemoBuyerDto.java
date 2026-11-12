package com.payresolve.shop.dto;

/**
 * Sandbox buyer credentials exposed solely to signed-in users when DEMO_MODE=true
 * and both DEMO_BUYER_EMAIL and DEMO_BUYER_PASSWORD are configured.
 */
public record DemoBuyerDto(
    String email,
    String password,
    String note
) {}

