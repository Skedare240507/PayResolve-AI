package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload sent to PayPal's POST /v1/notifications/verify-webhook-signature endpoint.
 */
public record VerifyWebhookSignatureRequest(
    @JsonProperty("auth_algo") String authAlgo,
    @JsonProperty("cert_url") String certUrl,
    @JsonProperty("transmission_id") String transmissionId,
    @JsonProperty("transmission_sig") String transmissionSig,
    @JsonProperty("transmission_time") String transmissionTime,
    @JsonProperty("webhook_id") String webhookId,
    @JsonProperty("webhook_event") Object webhookEvent
) {
}
