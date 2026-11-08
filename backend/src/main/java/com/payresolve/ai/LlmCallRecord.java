package com.payresolve.ai;

import java.math.BigDecimal;
import java.time.Instant;

public record LlmCallRecord(
    Long id,
    String caseId,
    String provider,
    String model,
    String callType,
    Integer promptTokens,
    Integer completionTokens,
    long latencyMs,
    BigDecimal costEstUsd,
    String status,
    boolean fallbackUsed,
    String fallbackReason,
    String snapshotHash,
    Instant createdAt
) {
    public static LlmCallRecord create(
        String caseId,
        String provider,
        String model,
        String callType,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        String status,
        boolean fallbackUsed,
        String fallbackReason,
        String snapshotHash,
        Instant now
    ) {
        return new LlmCallRecord(
            null,
            caseId,
            provider,
            model,
            callType,
            promptTokens,
            completionTokens,
            latencyMs,
            BigDecimal.ZERO.setScale(6),
            status,
            fallbackUsed,
            fallbackReason,
            snapshotHash,
            now
        );
    }
}

