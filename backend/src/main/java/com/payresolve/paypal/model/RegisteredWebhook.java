package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Webhook registration object returned by PayPal Webhooks API (GET /v1/notifications/webhooks).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisteredWebhook(
    @JsonProperty("id") String id,
    @JsonProperty("url") String url,
    @JsonProperty("event_types") List<WebhookEventType> eventTypes
) {
    public RegisteredWebhook {
        if (eventTypes == null) {
            eventTypes = Collections.emptyList();
        }
    }
}

