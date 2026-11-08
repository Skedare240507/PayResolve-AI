package com.payresolve.ingest.dto;

/**
 * Diagnostic attempt entry for the Event Inbox processing log.
 */
public record EventAttemptDto(
    String label,
    String statusText,
    boolean isError
) {}
