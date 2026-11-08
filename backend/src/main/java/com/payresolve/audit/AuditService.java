package com.payresolve.audit;

import com.payresolve.audit.dto.AuditLogEntryDto;
import com.payresolve.cases.dto.PagedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating append-only audit log recording and queries.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Appends an entry to the audit log.
     */
    @Transactional
    public void record(String actor, String action, String subjectType, String subjectId, Map<String, Object> details) {
        String detailsJson = "{}";
        if (details != null && !details.isEmpty()) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (Exception e) {
                log.warn("Failed to serialize audit log details for {} {}: {}", action, subjectId, e.getMessage());
            }
        }

        AuditLogRecord record = new AuditLogRecord(
            null,
            clock.instant(),
            actor != null ? actor : "system",
            action,
            subjectType,
            subjectId,
            detailsJson
        );

        auditLogRepository.insert(record);
    }

    /**
     * Queries audit log entries with optional filters and pagination.
     */
    public PagedResponse<AuditLogEntryDto> getAuditLogs(String search, String subjectType, int page, int size) {
        int offset = page * size;
        List<AuditLogRecord> records = auditLogRepository.findAll(search, subjectType, size, offset);
        long total = auditLogRepository.countAll(search, subjectType);

        List<AuditLogEntryDto> dtos = records.stream()
            .map(r -> AuditLogEntryDto.fromRecord(r, parseDetails(r.detailsJson())))
            .toList();

        return PagedResponse.of(dtos, page, size, total);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}

