package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Webhook event type subscription returned inside GET /v1/notifications/webhooks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookEventType(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("status") String status
) {
}

