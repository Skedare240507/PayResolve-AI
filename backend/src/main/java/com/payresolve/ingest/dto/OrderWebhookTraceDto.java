package com.payresolve.ingest.dto;

import com.payresolve.ingest.WebhookEvent;

import java.time.Instant;

/**
 * Lightweight projection of a webhook event used to build an order's payment trace timeline.
 */
public record OrderWebhookTraceDto(
    Long id,
    String paypalEventId,
    String eventType,
    String resourceId,
    boolean verified,
    String status,
    int attempts,
    Instant receivedAt,
    Instant processedAt,
    String lastError,
    Instant eventCreatedAt
) {
    public static OrderWebhookTraceDto from(WebhookEvent event) {
        return new OrderWebhookTraceDto(
            event.id(),
            event.paypalEventId(),
            event.eventType(),
            event.resourceId(),
            event.verified(),
            event.status(),
            event.attempts(),
            event.receivedAt(),
            event.processedAt(),
            event.lastError(),
            event.eventCreatedAt()
        );
    }
}

