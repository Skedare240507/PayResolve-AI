package com.payresolve.actions;

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
import java.util.Optional;

/**
 * JDBC data access for proposed actions in table `proposed_actions`.
 */
@Repository
public class ProposedActionRepository {

    private static final Logger log = LoggerFactory.getLogger(ProposedActionRepository.class);

    private final JdbcClient jdbcClient;
    private final ActionRowMapper rowMapper = new ActionRowMapper();

    public ProposedActionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ProposedActionRecord> findById(Long id) {
        String sql = """
            SELECT id, case_id, type, payload_json, idempotency_key, status, proposed_by, decided_by, decided_at, created_at
            FROM proposed_actions
            WHERE id = :id
            """;
        return jdbcClient.sql(sql)
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public Optional<ProposedActionRecord> findByIdempotencyKey(String idempotencyKey) {
        String sql = """
            SELECT id, case_id, type, payload_json, idempotency_key, status, proposed_by, decided_by, decided_at, created_at
            FROM proposed_actions
            WHERE idempotency_key = :idempotencyKey
            """;
        return jdbcClient.sql(sql)
            .param("idempotencyKey", idempotencyKey)
            .query(rowMapper)
            .optional();
    }

    public List<ProposedActionRecord> findByCaseId(String caseId) {
        String sql = """
            SELECT id, case_id, type, payload_json, idempotency_key, status, proposed_by, decided_by, decided_at, created_at
            FROM proposed_actions
            WHERE case_id = :caseId
            ORDER BY created_at DESC, id DESC
            """;
        return jdbcClient.sql(sql)
            .param("caseId", caseId)
            .query(rowMapper)
            .list();
    }

    public List<ProposedActionRecord> findAll(String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, case_id, type, payload_json, idempotency_key, status, proposed_by, decided_by, decided_at, created_at
            FROM proposed_actions
            WHERE 1=1
            """);

        if (status != null && !status.isBlank()) {
            sql.append(" AND UPPER(status) = UPPER(:status) ");
        }

        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset ");

        var query = jdbcClient.sql(sql.toString())
            .param("limit", limit)
            .param("offset", offset);

        if (status != null && !status.isBlank()) {
            query.param("status", status.trim());
        }

        return query.query(rowMapper).list();
    }

    public long countAll(String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM proposed_actions WHERE 1=1 ");

        if (status != null && !status.isBlank()) {
            sql.append(" AND UPPER(status) = UPPER(:status) ");
        }

        var query = jdbcClient.sql(sql.toString());

        if (status != null && !status.isBlank()) {
            query.param("status", status.trim());
        }

        return query.query(Long.class).single();
    }

    public Long insert(ProposedActionRecord record) {
        String sql = """
            INSERT INTO proposed_actions (
                case_id, type, payload_json, idempotency_key, status, proposed_by, decided_by, decided_at, created_at
            ) VALUES (
                :caseId, :type, CAST(:payloadJson AS jsonb), :idempotencyKey, :status, :proposedBy, :decidedBy, :decidedAt, :createdAt
            )
            """;

        Instant createdAt = record.createdAt() != null ? record.createdAt() : Instant.now();
        Timestamp decidedTs = record.decidedAt() != null ? Timestamp.from(record.decidedAt()) : null;

        jdbcClient.sql(sql)
            .param("caseId", record.caseId())
            .param("type", record.type().name())
            .param("payloadJson", record.payloadJson() != null ? record.payloadJson() : "{}")
            .param("idempotencyKey", record.idempotencyKey())
            .param("status", record.status().name())
            .param("proposedBy", record.proposedBy())
            .param("decidedBy", record.decidedBy())
            .param("decidedAt", decidedTs)
            .param("createdAt", Timestamp.from(createdAt))
            .update();

        // Retrieve newly inserted action by unique idempotency key
        return findByIdempotencyKey(record.idempotencyKey())
            .map(ProposedActionRecord::id)
            .orElseThrow(() -> new IllegalStateException("Failed to retrieve inserted proposed action"));
    }

    public void updateDecision(Long id, ActionStatus status, String decidedBy, Instant decidedAt) {
        String sql = """
            UPDATE proposed_actions
            SET status = :status,
                decided_by = :decidedBy,
                decided_at = :decidedAt
            WHERE id = :id
            """;

        Timestamp decidedTs = decidedAt != null ? Timestamp.from(decidedAt) : Timestamp.from(Instant.now());

        jdbcClient.sql(sql)
            .param("id", id)
            .param("status", status.name())
            .param("decidedBy", decidedBy)
            .param("decidedAt", decidedTs)
            .update();

        log.debug("Updated action decision: id={}, status={}, decidedBy={}", id, status, decidedBy);
    }

    private static class ActionRowMapper implements RowMapper<ProposedActionRecord> {
        @Override
        public ProposedActionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp decidedTs = rs.getTimestamp("decided_at");
            Timestamp createdTs = rs.getTimestamp("created_at");

            return new ProposedActionRecord(
                rs.getLong("id"),
                rs.getString("case_id"),
                ActionType.valueOf(rs.getString("type")),
                rs.getString("payload_json"),
                rs.getString("idempotency_key"),
                ActionStatus.valueOf(rs.getString("status")),
                rs.getString("proposed_by"),
                rs.getString("decided_by"),
                decidedTs != null ? decidedTs.toInstant() : null,
                createdTs != null ? createdTs.toInstant() : null
            );
        }
    }
}

