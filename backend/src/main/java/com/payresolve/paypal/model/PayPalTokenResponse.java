package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth 2.0 access token response from PayPal.
 */
public record PayPalTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("app_id") String appId,
    @JsonProperty("expires_in") Long expiresIn,
    @JsonProperty("scope") String scope
) {
}
