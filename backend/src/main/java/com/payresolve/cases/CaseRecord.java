package com.payresolve.cases;

import com.payresolve.common.Money;

import java.time.Instant;

/**
 * Domain entity representing an exception case persisted in the `cases` table.
 */
public record CaseRecord(
    String id,
    CaseType type,
    CaseStatus status,
    CaseSeverity severity,
    String paypalOrderId,
    String shopOrderId,
    Long amountMinor,
    String currency,
    Instant openedAt,
    Instant updatedAt,
    Instant resolvedAt,
    String summary
) {
    public Money amount() {
        if (amountMinor == null) {
            return null;
        }
        return Money.of(amountMinor, currency != null ? currency : "USD");
    }

    public boolean isOpen() {
        return status == CaseStatus.OPEN;
    }

    public boolean isResolved() {
        return status == CaseStatus.RESOLVED;
    }

    public boolean isDismissed() {
        return status == CaseStatus.DISMISSED;
    }
}
