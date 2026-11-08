package com.payresolve.actions;

import java.time.Instant;

/**
 * Domain entity mapping to table `proposed_actions`.
 */
public record ProposedActionRecord(
    Long id,
    String caseId,
    ActionType type,
    String payloadJson,
    String idempotencyKey,
    ActionStatus status,
    String proposedBy,
    String decidedBy,
    Instant decidedAt,
    Instant createdAt
) {}

