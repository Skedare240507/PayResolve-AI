package com.payresolve.ingest.dto;

import com.payresolve.ingest.WebhookEvent;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Event inbox presentation model matching frontend contract and design specs.
 */
public record EventInboxItemDto(
    Long id,
    String eventId,
    String paypalEventId,
    String type,
    String resource,
    String signature, // "Verified" | "Unverified"
    String status,    // "Processed" | "Failed" | "Duplicate" | "Received"
    int tries,
    int deliveryCount,
    String at,
    Instant receivedAt,
    Instant processedAt,
    String rawPayload,
    List<EventAttemptDto> attempts,
    String lastError
) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    public static EventInboxItemDto from(WebhookEvent event) {
        String sig = event.verified() ? "Verified" : "Unverified";

        String statusDisplay;
        if ("PROCESSED".equalsIgnoreCase(event.status())) {
            statusDisplay = "Processed";
        } else if ("FAILED".equalsIgnoreCase(event.status()) || "UNVERIFIED".equalsIgnoreCase(event.status())) {
            statusDisplay = "Failed";
        } else if ("DUPLICATE".equalsIgnoreCase(event.status())) {
            statusDisplay = "Duplicate";
        } else {
            statusDisplay = "Received";
        }

        String atFormatted = event.receivedAt() != null ? TIME_FORMATTER.format(event.receivedAt()) : "";

        String resourceDisplay = event.resourceId() != null ? event.resourceId() : "resource";
        if (event.eventType() != null) {
            if (event.eventType().contains("CAPTURE")) {
                resourceDisplay = "capture " + (event.resourceId() != null ? event.resourceId() : "");
            } else if (event.eventType().contains("ORDER")) {
                resourceDisplay = "order " + (event.resourceId() != null ? event.resourceId() : "");
            } else if (event.eventType().contains("REFUND")) {
                resourceDisplay = "refund " + (event.resourceId() != null ? event.resourceId() : "");
            }
        }

        List<EventAttemptDto> attemptsList = new ArrayList<>();
        if (event.attempts() == 0) {
            if (!event.verified()) {
                attemptsList.add(new EventAttemptDto("Attempt 1", "signature verification failed", true));
            } else {
                attemptsList.add(new EventAttemptDto("Attempt 1", "enqueued for processing", false));
            }
        } else {
            for (int i = 1; i <= event.attempts(); i++) {
                boolean isLast = (i == event.attempts());
                if (isLast && "PROCESSED".equalsIgnoreCase(event.status())) {
                    attemptsList.add(new EventAttemptDto("Attempt " + i, "handler succeeded", false));
                } else if (isLast && event.lastError() != null) {
                    attemptsList.add(new EventAttemptDto("Attempt " + i, "failed " + event.lastError(), true));
                } else {
                    attemptsList.add(new EventAttemptDto("Attempt " + i, "failed handler error", true));
                }
            }
        }

        return new EventInboxItemDto(
            event.id(),
            event.paypalEventId(),
            event.paypalEventId(),
            event.eventType(),
            resourceDisplay.trim(),
            sig,
            statusDisplay,
            event.attempts(),
            event.deliveryCount(),
            atFormatted,
            event.receivedAt(),
            event.processedAt(),
            event.rawBody(),
            attemptsList,
            event.lastError()
        );
    }
}
