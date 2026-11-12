package com.payresolve.cases.dto;

public record CaseQueueStatsDto(
    long total,
    long open,
    long investigating,
    long actionProposed,
    long resolved,
    long dismissed,
    long openCount,
    long awaitingApprovalCount,
    long duplicatesBlockedToday,
    long oldestOpenMinutes
) {}

