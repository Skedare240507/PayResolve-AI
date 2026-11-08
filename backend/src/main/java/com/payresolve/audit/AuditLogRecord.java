package com.payresolve.audit;

import java.time.Instant;

/**
 * Append-only audit log domain entity mapping to table `audit_log`.
 */
public record AuditLogRecord(
    Long id,
    Instant at,
    String actor,
    String action,
    String subjectType,
    String subjectId,
    String detailsJson
) {}

