package com.payresolve.investigator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * REST API response DTO for an investigation and its associated tool execution steps.
 */
public record InvestigationResponseDto(
    @JsonProperty("id") String id,
    @JsonProperty("caseId") String caseId,
    @JsonProperty("status") String status,
    @JsonProperty("report") InvestigationReport report,
    @JsonProperty("model") String model,
    @JsonProperty("startedAt") Instant startedAt,
    @JsonProperty("finishedAt") Instant finishedAt,
    @JsonProperty("steps") List<InvestigationStepDto> steps
) {
    public InvestigationResponseDto {
        steps = steps != null ? List.copyOf(steps) : List.of();
    }
}

