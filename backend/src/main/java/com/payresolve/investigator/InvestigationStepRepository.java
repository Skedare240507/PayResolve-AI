package com.payresolve.investigator;

import com.payresolve.investigator.model.InvestigationStepRecord;
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
 * JDBC data access for auditable tool invocation steps in `investigation_steps`.
 */
@Repository
public class InvestigationStepRepository {

    private static final Logger log = LoggerFactory.getLogger(InvestigationStepRepository.class);

    private final JdbcClient jdbcClient;
    private final StepRowMapper rowMapper = new StepRowMapper();

    public InvestigationStepRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<InvestigationStepRecord> findByInvestigationId(String investigationId) {
        String sql = """
            SELECT id, investigation_id, tool, args_json, result_digest, started_at, ms
            FROM investigation_steps
            WHERE investigation_id = :investigationId
            ORDER BY id ASC
            """;
        return jdbcClient.sql(sql)
            .param("investigationId", investigationId)
            .query(rowMapper)
            .list();
    }

    public void insert(InvestigationStepRecord record) {
        String sql = """
            INSERT INTO investigation_steps (
                investigation_id, tool, args_json, result_digest, started_at, ms
            ) VALUES (
                :investigationId, :tool, CAST(:argsJson AS jsonb), :resultDigest, :startedAt, :ms
            )
            """;

        Instant started = record.startedAt() != null ? record.startedAt() : Instant.now();

        jdbcClient.sql(sql)
            .param("investigationId", record.investigationId())
            .param("tool", record.tool())
            .param("argsJson", record.argsJson() != null ? record.argsJson() : "{}")
            .param("resultDigest", record.resultDigest())
            .param("startedAt", Timestamp.from(started))
            .param("ms", record.ms())
            .update();

        log.debug("Recorded tool step: invId={}, tool={}, ms={}", record.investigationId(), record.tool(), record.ms());
    }

    private static class StepRowMapper implements RowMapper<InvestigationStepRecord> {
        @Override
        public InvestigationStepRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp startedTs = rs.getTimestamp("started_at");
            return new InvestigationStepRecord(
                rs.getLong("id"),
                rs.getString("investigation_id"),
                rs.getString("tool"),
                rs.getString("args_json"),
                rs.getString("result_digest"),
                startedTs != null ? startedTs.toInstant() : null,
                rs.getLong("ms")
            );
        }
    }
}

