package com.payresolve.evidence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Single chronological item on the case evidence timeline.
 */
public record EvidenceTimelineItemDto(
    @JsonProperty("id") String id,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("formattedTime") String formattedTime,
    @JsonProperty("actor") String actor,
    @JsonProperty("source") String source,
    @JsonProperty("type") String type,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("isDiscrepancy") boolean isDiscrepancy,
    @JsonProperty("externalId") String externalId
) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneOffset.UTC);

    public static EvidenceTimelineItemDto of(
        String id,
        Instant timestamp,
        String actor,
        String source,
        String type,
        String title,
        String description,
        boolean isDiscrepancy,
        String externalId
    ) {
        String formattedTime = timestamp != null ? TIME_FORMATTER.format(timestamp) : "--:--:--";
        return new EvidenceTimelineItemDto(
            id, timestamp, formattedTime, actor, source, type, title, description, isDiscrepancy, externalId
        );
    }
}

