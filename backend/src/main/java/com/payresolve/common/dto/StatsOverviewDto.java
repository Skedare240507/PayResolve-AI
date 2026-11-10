package com.payresolve.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated read-only operational telemetry for dashboard charts.
 * Contains strictly real database aggregates (empty lists when no data exists).
 */
public record StatsOverviewDto(
    Instant generatedAt,
    int totalCases,
    List<CategoryCountDto> casesByType,
    List<SeverityCountDto> casesBySeverity,
    List<EvidenceCoverageByTypeDto> evidenceCoverageByType,
    List<HourlyWebhookStatDto> webhooksByHour,
    BenchmarkComparisonDto benchmarkComparison
) {
    public record CategoryCountDto(
        String type,
        String typeLabel,
        int count
    ) {}

    public record SeverityCountDto(
        String severity,
        int count
    ) {}

    public record EvidenceCoverageByTypeDto(
        String type,
        String typeLabel,
        int confirmedNodes,
        int missingNodes
    ) {}

    public record HourlyWebhookStatDto(
        String hourIso,
        String hourLabel,
        int totalReceived,
        int duplicateDeliveries
    ) {}

    public record BenchmarkComparisonDto(
        String benchmarkId,
        Instant runAt,
        int sampleSize,
        int passedCases,
        double ruleTimeMs,
        double aiInvestigatorTimeMs,
        double precisionPct,
        double recallPct,
        double groundingRatePct
    ) {}
}
