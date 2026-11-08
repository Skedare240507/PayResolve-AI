package com.payresolve.evidence;

import java.time.Instant;

/**
 * Domain record representing an immutable evidence item persisted in `case_evidence`.
 */
public record CaseEvidenceRecord(
    String id,
    String caseId,
    EvidenceKind kind,
    EvidenceSource source,
    String externalId,
    String payloadJson,
    Instant observedAt
) {
}

