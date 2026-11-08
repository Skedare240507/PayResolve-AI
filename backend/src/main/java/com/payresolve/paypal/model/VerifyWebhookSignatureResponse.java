package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result returned by PayPal's POST /v1/notifications/verify-webhook-signature endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyWebhookSignatureResponse(
    @JsonProperty("verification_status") String verificationStatus
) {
    /**
     * Checks if verification was accepted by PayPal.
     */
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(verificationStatus);
    }
}
