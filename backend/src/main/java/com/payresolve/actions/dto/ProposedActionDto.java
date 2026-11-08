package com.payresolve.actions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.actions.ProposedActionRecord;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing a proposed action returned by the REST API.
 */
public record ProposedActionDto(
    @JsonProperty("id") Long id,
    @JsonProperty("caseId") String caseId,
    @JsonProperty("type") String type,
    @JsonProperty("status") String status,
    @JsonProperty("idempotencyKey") String idempotencyKey,
    @JsonProperty("payload") Map<String, Object> payload,
    @JsonProperty("proposedBy") String proposedBy,
    @JsonProperty("decidedBy") String decidedBy,
    @JsonProperty("decidedAt") Instant decidedAt,
    @JsonProperty("createdAt") Instant createdAt
) {
    public static ProposedActionDto fromRecord(ProposedActionRecord record, Map<String, Object> payload) {
        return new ProposedActionDto(
            record.id(),
            record.caseId(),
            record.type().name(),
            record.status().name(),
            record.idempotencyKey(),
            payload != null ? Map.copyOf(payload) : Map.of(),
            record.proposedBy(),
            record.decidedBy(),
            record.decidedAt(),
            record.createdAt()
        );
    }
}

