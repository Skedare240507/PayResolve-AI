package com.payresolve.ingest;

/**
 * Result of ingesting a PayPal webhook notification.
 */
public record IngestResult(
    Long id,
    String paypalEventId,
    String eventType,
    boolean verified,
    String status,
    boolean duplicate,
    boolean dropped
) {
    public static IngestResult dropped(String paypalEventId) {
        return new IngestResult(null, paypalEventId, null, false, "DROPPED", false, true);
    }

    public static IngestResult duplicate(String paypalEventId) {
        return new IngestResult(null, paypalEventId, null, true, "DUPLICATE", true, false);
    }

    public static IngestResult duplicate(Long id, String paypalEventId, String eventType, boolean verified, String status) {
        return new IngestResult(id, paypalEventId, eventType, verified, status, true, false);
    }

    public static IngestResult accepted(Long id, String paypalEventId, String eventType, boolean verified, String status) {
        return new IngestResult(id, paypalEventId, eventType, verified, status, false, false);
    }
}
