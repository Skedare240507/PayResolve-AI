package com.payresolve.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Operator-only PayPal Sandbox diagnostics report containing non-secret configuration
 * checks, live OAuth/webhook verification status, and actionable fix hints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalDiagnosticsDto(
    boolean overallOk,
    Instant checkedAt,
    String sandboxBaseUrl,
    String expectedWebhookUrl,
    String appId,
    String businessAccount,
    Instant lastWebhookReceivedAt,
    List<DiagnosticCheckDto> checks
) {
}

