package com.payresolve.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.audit.AuditLogRecord;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing an entry in the system audit log.
 */
public record AuditLogEntryDto(
    @JsonProperty("id") Long id,
    @JsonProperty("at") Instant at,
    @JsonProperty("actor") String actor,
    @JsonProperty("action") String action,
    @JsonProperty("subjectType") String subjectType,
    @JsonProperty("subjectId") String subjectId,
    @JsonProperty("details") Map<String, Object> details
) {
    public static AuditLogEntryDto fromRecord(AuditLogRecord record, Map<String, Object> details) {
        return new AuditLogEntryDto(
            record.id(),
            record.at(),
            record.actor(),
            record.action(),
            record.subjectType(),
            record.subjectId(),
            details != null ? Map.copyOf(details) : Map.of()
        );
    }
}

