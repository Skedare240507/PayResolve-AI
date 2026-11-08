package com.payresolve.cases;

import com.payresolve.common.Money;

import java.util.Map;

/**
 * Result produced by a Detector function when a discrepancy is detected.
 */
public record DetectionFinding(
    CaseType type,
    CaseSeverity severity,
    String paypalOrderId,
    String shopOrderId,
    Money orderAmount,
    Money discrepancyAmount,
    String summary,
    Map<String, Object> details
) {
    public DetectionFinding {
        details = details != null ? Map.copyOf(details) : Map.of();
    }

    public static DetectionFinding of(
        CaseType type,
        CaseSeverity severity,
        String paypalOrderId,
        String shopOrderId,
        Money orderAmount,
        Money discrepancyAmount,
        String summary
    ) {
        return new DetectionFinding(type, severity, paypalOrderId, shopOrderId, orderAmount, discrepancyAmount, summary, Map.of());
    }

    public static DetectionFinding of(
        CaseType type,
        CaseSeverity severity,
        String paypalOrderId,
        String shopOrderId,
        Money orderAmount,
        String summary
    ) {
        return new DetectionFinding(type, severity, paypalOrderId, shopOrderId, orderAmount, null, summary, Map.of());
    }
}
