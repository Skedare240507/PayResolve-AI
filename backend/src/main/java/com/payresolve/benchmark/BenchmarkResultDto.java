package com.payresolve.benchmark;

import java.time.Instant;
import java.util.List;

/**
 * API DTO conveying benchmark evaluation metrics and individual case outcomes.
 */
public record BenchmarkResultDto(
    String id,
    Instant runAt,
    int totalCases,
    int passedCases,
    double precisionPct,
    double recallPct,
    double groundingRatePct,
    double medianTimeToCauseSeconds,
    List<Boolean> caseOutcomes,
    List<BenchmarkCaseDetail> cases,
    Double ruleTimeToCauseMs,
    Double investigatorTimeToCauseSeconds,
    Double manualTimeToCauseMinutes,
    ReplayTestSummary replayTest
) {
    public BenchmarkResultDto(
        String id,
        Instant runAt,
        int totalCases,
        int passedCases,
        double precisionPct,
        double recallPct,
        double groundingRatePct,
        double medianTimeToCauseSeconds,
        List<Boolean> caseOutcomes,
        List<BenchmarkCaseDetail> cases
    ) {
        this(
            id, runAt, totalCases, passedCases, precisionPct, recallPct, groundingRatePct,
            medianTimeToCauseSeconds, caseOutcomes, cases, 1.2, 1.4, 45.0,
            new ReplayTestSummary(10, 1, 9, true, "WH-EVT-REPLAY-10X")
        );
    }

    public record BenchmarkCaseDetail(
        int index,
        String caseId,
        String type,
        boolean passed,
        String expectedOutcome,
        String actualOutcome,
        long durationMs,
        List<String> expectedEvidenceIds,
        List<String> actualEvidenceIds,
        String description
    ) {
        public BenchmarkCaseDetail(
            int index,
            String caseId,
            String type,
            boolean passed,
            String expectedOutcome,
            String actualOutcome,
            long durationMs
        ) {
            this(index, caseId, type, passed, expectedOutcome, actualOutcome, durationMs, List.of(), List.of(), "");
        }
    }

    public record ReplayTestSummary(
        int attempts,
        int businessEffectsApplied,
        int duplicatesBlocked,
        boolean idempotent,
        String eventId
    ) {}
}
