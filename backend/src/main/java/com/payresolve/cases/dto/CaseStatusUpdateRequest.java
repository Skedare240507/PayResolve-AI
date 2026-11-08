package com.payresolve.cases.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for transitioning a case's lifecycle status.
 */
public record CaseStatusUpdateRequest(
    @NotBlank(message = "status is required")
    @JsonProperty("status") String status
) {
}
