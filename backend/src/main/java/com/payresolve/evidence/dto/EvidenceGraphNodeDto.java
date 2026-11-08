package com.payresolve.evidence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Representation of a node in the Case Evidence Graph.
 */
public record EvidenceGraphNodeDto(
    @JsonProperty("id") String id,
    @JsonProperty("label") String label,
    @JsonProperty("type") String type,
    @JsonProperty("source") String source,
    @JsonProperty("externalId") String externalId,
    @JsonProperty("status") String status,
    @JsonProperty("observedAt") Instant observedAt,
    @JsonProperty("isMissing") boolean isMissing,
    @JsonProperty("missingReason") String missingReason,
    @JsonProperty("metadata") Map<String, Object> metadata
) {
    public EvidenceGraphNodeDto {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static EvidenceGraphNodeDto of(
        String id,
        String label,
        String type,
        String source,
        String externalId,
        String status,
        Instant observedAt,
        Map<String, Object> metadata
    ) {
        return new EvidenceGraphNodeDto(id, label, type, source, externalId, status, observedAt, false, null, metadata);
    }

    public static EvidenceGraphNodeDto missing(
        String id,
        String label,
        String type,
        String source,
        String missingReason,
        Instant observedAt
    ) {
        return new EvidenceGraphNodeDto(id, label, type, source, null, "MISSING", observedAt, true, missingReason, Map.of());
    }
}

