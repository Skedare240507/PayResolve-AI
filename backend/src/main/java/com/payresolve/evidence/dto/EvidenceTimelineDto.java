package com.payresolve.evidence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ordered timeline of events associated with an exception case.
 */
public record EvidenceTimelineDto(
    @JsonProperty("caseId") String caseId,
    @JsonProperty("items") List<EvidenceTimelineItemDto> items,
    @JsonProperty("totalEvents") int totalEvents
) {
    public EvidenceTimelineDto {
        items = items != null ? List.copyOf(items) : List.of();
    }
}

