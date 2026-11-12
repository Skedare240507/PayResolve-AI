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
    @JsonProperty("steps") List<InvestigationStepDto> steps,
    @JsonProperty("verifiedCitations") Integer verifiedCitations,
    @JsonProperty("totalCitations") Integer totalCitations,
    @JsonProperty("provider") String provider,
    @JsonProperty("providerMode") String providerMode,
    @JsonProperty("fallbackReason") String fallbackReason,
    @JsonProperty("latencyMs") Long latencyMs,
    @JsonProperty("promptTokens") Integer promptTokens,
    @JsonProperty("completionTokens") Integer completionTokens
) {
    public InvestigationResponseDto(
        String id,
        String caseId,
        String status,
        InvestigationReport report,
        String model,
        Instant startedAt,
        Instant finishedAt,
        List<InvestigationStepDto> steps
    ) {
        this(
            id,
            caseId,
            status,
            report,
            model,
            startedAt,
            finishedAt,
            steps,
            report != null ? report.verifiedCitations() : null,
            report != null ? report.totalCitations() : null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public InvestigationResponseDto {
        steps = steps != null ? List.copyOf(steps) : List.of();
    }
}
