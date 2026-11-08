package com.payresolve.ingest.dto;

/**
 * Summary metrics for the Event Inbox stat cards (Received, Verified, Processed, Failed/Duplicate).
 */
public record EventStatsDto(
    long received,
    long verified,
    long processed,
    long failedOrDuplicate
) {}
