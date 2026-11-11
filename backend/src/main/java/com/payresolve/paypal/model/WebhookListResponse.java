package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Response payload from PayPal Webhooks API: GET /v1/notifications/webhooks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookListResponse(
    @JsonProperty("webhooks") List<RegisteredWebhook> webhooks
) {
    public WebhookListResponse {
        if (webhooks == null) {
            webhooks = Collections.emptyList();
        }
    }
}

