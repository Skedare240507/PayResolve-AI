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
    Map<String, Object> details,
    CaseStatus initialStatus
) {
    public DetectionFinding {
        details = details != null ? Map.copyOf(details) : Map.of();
        initialStatus = initialStatus != null ? initialStatus : CaseStatus.OPEN;
    }

    public DetectionFinding(
        CaseType type,
        CaseSeverity severity,
        String paypalOrderId,
        String shopOrderId,
        Money orderAmount,
        Money discrepancyAmount,
        String summary,
        Map<String, Object> details
    ) {
        this(type, severity, paypalOrderId, shopOrderId, orderAmount, discrepancyAmount, summary, details, CaseStatus.OPEN);
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
        return new DetectionFinding(type, severity, paypalOrderId, shopOrderId, orderAmount, discrepancyAmount, summary, Map.of(), CaseStatus.OPEN);
    }

    public static DetectionFinding of(
        CaseType type,
        CaseSeverity severity,
        String paypalOrderId,
        String shopOrderId,
        Money orderAmount,
        String summary
    ) {
        return new DetectionFinding(type, severity, paypalOrderId, shopOrderId, orderAmount, null, summary, Map.of(), CaseStatus.OPEN);
    }

    public static DetectionFinding resolved(
        CaseType type,
        CaseSeverity severity,
        String paypalOrderId,
        String shopOrderId,
        Money orderAmount,
        String summary
    ) {
        return new DetectionFinding(type, severity, paypalOrderId, shopOrderId, orderAmount, null, summary, Map.of(), CaseStatus.RESOLVED);
    }
}
