package com.payresolve.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class LlmCallRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<LlmCallRecord> rowMapper = (rs, rowNum) -> new LlmCallRecord(
        rs.getLong("id"),
        rs.getString("case_id"),
        rs.getString("provider"),
        rs.getString("model"),
        rs.getString("call_type"),
        rs.getObject("prompt_tokens") != null ? rs.getInt("prompt_tokens") : null,
        rs.getObject("completion_tokens") != null ? rs.getInt("completion_tokens") : null,
        rs.getLong("latency_ms"),
        rs.getBigDecimal("cost_est_usd"),
        rs.getString("status"),
        rs.getBoolean("fallback_used"),
        rs.getString("fallback_reason"),
        rs.getString("snapshot_hash"),
        rs.getTimestamp("created_at").toInstant()
    );

    public LlmCallRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(LlmCallRecord record) {
        String sql = """
            INSERT INTO llm_calls (
                case_id, provider, model, call_type, prompt_tokens, completion_tokens,
                latency_ms, cost_est_usd, status, fallback_used, fallback_reason,
                snapshot_hash, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(
            sql,
            record.caseId(),
            record.provider(),
            record.model(),
            record.callType(),
            record.promptTokens(),
            record.completionTokens(),
            record.latencyMs(),
            record.costEstUsd(),
            record.status(),
            record.fallbackUsed(),
            record.fallbackReason(),
            record.snapshotHash(),
            Timestamp.from(record.createdAt())
        );
    }

    public List<LlmCallRecord> findByCaseId(String caseId) {
        String sql = "SELECT * FROM llm_calls WHERE case_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, caseId);
    }

    public List<LlmCallRecord> findRecent(int limit) {
        String sql = "SELECT * FROM llm_calls ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, limit);
    }

    public Optional<LlmCallRecord> findLatestSuccessfulBySnapshotHash(String snapshotHash) {
        String sql = "SELECT * FROM llm_calls WHERE snapshot_hash = ? AND status = 'SUCCESS' ORDER BY created_at DESC LIMIT 1";
        List<LlmCallRecord> results = jdbcTemplate.query(sql, rowMapper, snapshotHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}

