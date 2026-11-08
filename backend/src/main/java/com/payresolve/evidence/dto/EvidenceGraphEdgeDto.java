package com.payresolve.evidence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Directed relationship connecting two nodes in the Case Evidence Graph.
 */
public record EvidenceGraphEdgeDto(
    @JsonProperty("id") String id,
    @JsonProperty("source") String source,
    @JsonProperty("target") String target,
    @JsonProperty("type") String type,
    @JsonProperty("label") String label,
    @JsonProperty("isDashed") boolean isDashed,
    @JsonProperty("status") String status
) {
    public static EvidenceGraphEdgeDto solid(String id, String source, String target, String type, String label) {
        return new EvidenceGraphEdgeDto(id, source, target, type, label, false, "VALID");
    }

    public static EvidenceGraphEdgeDto dashed(String id, String source, String target, String type, String label) {
        return new EvidenceGraphEdgeDto(id, source, target, type, label, true, "MISSING");
    }
}

