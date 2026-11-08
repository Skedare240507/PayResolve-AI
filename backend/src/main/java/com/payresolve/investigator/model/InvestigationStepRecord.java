package com.payresolve.investigator.model;

import java.time.Instant;

/**
 * Domain entity mapping to table `investigation_steps` for auditability and trace logs.
 */
public record InvestigationStepRecord(
    Long id,
    String investigationId,
    String tool,
    String argsJson,
    String resultDigest,
    Instant startedAt,
    long ms
) {}

