package com.payresolve.ingest;

/**
 * Domain event published when a verified duplicate webhook delivery is absorbed idempotently.
 */
public record DuplicateWebhookDeliveredEvent(String paypalEventId) {}

