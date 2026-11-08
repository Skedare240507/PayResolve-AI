package com.payresolve.actions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.actions.ActionType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request payload to propose an action for a case.
 */
public record ProposeActionRequest(
    @NotNull @JsonProperty("type") ActionType type,
    @JsonProperty("payload") Map<String, Object> payload,
    @JsonProperty("idempotencyKey") String idempotencyKey,
    @JsonProperty("proposedBy") String proposedBy
) {}

