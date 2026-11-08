package com.payresolve.investigator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing an auditable tool invocation step for the UI trace.
 */
public record InvestigationStepDto(
    @JsonProperty("id") Long id,
    @JsonProperty("tool") String tool,
    @JsonProperty("args") Map<String, Object> args,
    @JsonProperty("resultDigest") String resultDigest,
    @JsonProperty("startedAt") Instant startedAt,
    @JsonProperty("latencyMs") long latencyMs
) {}

