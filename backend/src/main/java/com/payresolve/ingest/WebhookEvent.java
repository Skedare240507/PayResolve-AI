package com.payresolve.ingest;

import java.time.Instant;

/**
 * Domain entity representing an ingested PayPal webhook event in the durable event inbox.
 */
public record WebhookEvent(
    Long id,
    String paypalEventId,
    String eventType,
    String resourceId,
    String rawBody,
    String headersJson,
    boolean verified,
    String status,
    int attempts,
    Instant receivedAt,
    Instant processedAt,
    String lastError,
    int deliveryCount,
    Instant lastReceivedAt,
    Instant eventCreatedAt,
    String customId,
    String paypalOrderId
) {
    public WebhookEvent(
        Long id,
        String paypalEventId,
        String eventType,
        String resourceId,
        String rawBody,
        String headersJson,
        boolean verified,
        String status,
        int attempts,
        Instant receivedAt,
        Instant processedAt,
        String lastError
    ) {
        this(id, paypalEventId, eventType, resourceId, rawBody, headersJson, verified, status, attempts,
             receivedAt, processedAt, lastError, 1, receivedAt, null, null, null);
    }

    public WebhookEvent(
        Long id,
        String paypalEventId,
        String eventType,
        String resourceId,
        String rawBody,
        String headersJson,
        boolean verified,
        String status,
        int attempts,
        int deliveryCount,
        Instant receivedAt,
        Instant processedAt,
        String lastError
    ) {
        this(id, paypalEventId, eventType, resourceId, rawBody, headersJson, verified, status, attempts,
             receivedAt, processedAt, lastError, deliveryCount, receivedAt, null, null, null);
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }

    public boolean isProcessed() {
        return "PROCESSED".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }

    public boolean isUnverified() {
        return "UNVERIFIED".equalsIgnoreCase(status);
    }
}
