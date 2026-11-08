package com.payresolve.investigator.model;

import java.time.Instant;

/**
 * Domain entity mapping to table `investigations`.
 */
public record InvestigationRecord(
    String id,
    String caseId,
    InvestigationStatus status,
    String reportJson,
    String model,
    Instant startedAt,
    Instant finishedAt
) {}

