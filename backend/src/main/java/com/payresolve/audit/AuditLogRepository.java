package com.payresolve.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * JDBC data access for the append-only `audit_log` table.
 */
@Repository
public class AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);

    private final JdbcClient jdbcClient;
    private final AuditLogRowMapper rowMapper = new AuditLogRowMapper();

    public AuditLogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(AuditLogRecord record) {
        String sql = """
            INSERT INTO audit_log (
                at, actor, action, subject_type, subject_id, details_json
            ) VALUES (
                :at, :actor, :action, :subjectType, :subjectId, CAST(:detailsJson AS jsonb)
            )
            """;

        Instant at = record.at() != null ? record.at() : Instant.now();

        jdbcClient.sql(sql)
            .param("at", Timestamp.from(at))
            .param("actor", record.actor())
            .param("action", record.action())
            .param("subjectType", record.subjectType())
            .param("subjectId", record.subjectId())
            .param("detailsJson", record.detailsJson() != null ? record.detailsJson() : "{}")
            .update();

        log.debug("Appended audit entry: actor={}, action={}, subject={}:{}",
            record.actor(), record.action(), record.subjectType(), record.subjectId());
    }

    public List<AuditLogRecord> findAll(String search, String subjectType, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, at, actor, action, subject_type, subject_id, details_json
            FROM audit_log
            WHERE 1=1
            """);

        if (subjectType != null && !subjectType.isBlank()) {
            sql.append(" AND UPPER(subject_type) = UPPER(:subjectType) ");
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (UPPER(actor) LIKE UPPER(:search) OR UPPER(action) LIKE UPPER(:search) OR UPPER(subject_id) LIKE UPPER(:search)) ");
        }

        sql.append(" ORDER BY at DESC, id DESC LIMIT :limit OFFSET :offset ");

        var query = jdbcClient.sql(sql.toString())
            .param("limit", limit)
            .param("offset", offset);

        if (subjectType != null && !subjectType.isBlank()) {
            query.param("subjectType", subjectType.trim());
        }
        if (search != null && !search.isBlank()) {
            query.param("search", "%" + search.trim() + "%");
        }

        return query.query(rowMapper).list();
    }

    public long countAll(String search, String subjectType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_log WHERE 1=1 ");

        if (subjectType != null && !subjectType.isBlank()) {
            sql.append(" AND UPPER(subject_type) = UPPER(:subjectType) ");
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (UPPER(actor) LIKE UPPER(:search) OR UPPER(action) LIKE UPPER(:search) OR UPPER(subject_id) LIKE UPPER(:search)) ");
        }

        var query = jdbcClient.sql(sql.toString());

        if (subjectType != null && !subjectType.isBlank()) {
            query.param("subjectType", subjectType.trim());
        }
        if (search != null && !search.isBlank()) {
            query.param("search", "%" + search.trim() + "%");
        }

        return query.query(Long.class).single();
    }

    private static class AuditLogRowMapper implements RowMapper<AuditLogRecord> {
        @Override
        public AuditLogRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp ts = rs.getTimestamp("at");
            return new AuditLogRecord(
                rs.getLong("id"),
                ts != null ? ts.toInstant() : null,
                rs.getString("actor"),
                rs.getString("action"),
                rs.getString("subject_type"),
                rs.getString("subject_id"),
                rs.getString("details_json")
            );
        }
    }
}

