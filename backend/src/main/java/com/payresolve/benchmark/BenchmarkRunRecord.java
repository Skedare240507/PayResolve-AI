package com.payresolve.benchmark;

import java.time.Instant;

/**
 * Domain record mapping to the benchmark_runs table.
 */
public record BenchmarkRunRecord(
    String id,
    Instant runAt,
    int totalCases,
    int passedCases,
    double precisionPct,
    double recallPct,
    long avgDurationMs,
    String resultsJson
) {}

