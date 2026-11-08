package com.payresolve.cases.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result returned after executing detectors on demand.
 */
public record DetectionRunResultDto(
    @JsonProperty("totalEvaluated") int totalEvaluated,
    @JsonProperty("casesOpened") int casesOpened,
    @JsonProperty("casesUpdated") int casesUpdated,
    @JsonProperty("totalCases") long totalCases,
    @JsonProperty("message") String message
) {
}
