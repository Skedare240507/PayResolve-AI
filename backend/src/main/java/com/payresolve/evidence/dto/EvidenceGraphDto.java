package com.payresolve.evidence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Derived Evidence Graph returned to the client at read time.
 */
public record EvidenceGraphDto(
    @JsonProperty("caseId") String caseId,
    @JsonProperty("caseType") String caseType,
    @JsonProperty("nodes") List<EvidenceGraphNodeDto> nodes,
    @JsonProperty("edges") List<EvidenceGraphEdgeDto> edges,
    @JsonProperty("missingNodesCount") int missingNodesCount,
    @JsonProperty("summary") String summary
) {
    public EvidenceGraphDto {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
    }
}

