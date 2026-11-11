package com.payresolve.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Single checklist item produced by GET /api/paypal/diagnostics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosticCheckDto(
    String id,
    String label,
    String status,
    boolean passed,
    String detail,
    String fixHint
) {
}

