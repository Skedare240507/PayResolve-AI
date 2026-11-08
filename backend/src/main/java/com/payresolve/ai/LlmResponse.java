package com.payresolve.ai;

public record LlmResponse(
    String content,
    String provider,
    String model,
    Integer promptTokens,
    Integer completionTokens,
    long latencyMs,
    boolean fallbackUsed,
    String fallbackReason
) {}

