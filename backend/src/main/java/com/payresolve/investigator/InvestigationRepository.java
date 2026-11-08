package com.payresolve.investigator;

import com.payresolve.investigator.model.InvestigationRecord;
import com.payresolve.investigator.model.InvestigationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC data access for investigations in table `investigations`.
 */
@Repository
public class InvestigationRepository {

    private static final Logger log = LoggerFactory.getLogger(InvestigationRepository.class);

    private final JdbcClient jdbcClient;
    private final InvestigationRowMapper rowMapper = new InvestigationRowMapper();

    public InvestigationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<InvestigationRecord> findById(String id) {
        String sql = """
            SELECT id, case_id, status, report_json, model, started_at, finished_at
            FROM investigations
            WHERE id = :id
            """;
        return jdbcClient.sql(sql)
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public Optional<InvestigationRecord> findRecentByCaseId(String caseId) {
        String sql = """
            SELECT id, case_id, status, report_json, model, started_at, finished_at
            FROM investigations
            WHERE case_id = :caseId
            ORDER BY started_at DESC
            LIMIT 1
            """;
        return jdbcClient.sql(sql)
            .param("caseId", caseId)
            .query(rowMapper)
            .optional();
    }

    public void insert(InvestigationRecord record) {
        String sql = """
            INSERT INTO investigations (
                id, case_id, status, report_json, model, started_at, finished_at
            ) VALUES (
                :id, :caseId, :status, CAST(:reportJson AS jsonb), :model, :startedAt, :finishedAt
            )
            """;

        Instant started = record.startedAt() != null ? record.startedAt() : Instant.now();
        Timestamp finishedTs = record.finishedAt() != null ? Timestamp.from(record.finishedAt()) : null;

        jdbcClient.sql(sql)
            .param("id", record.id())
            .param("caseId", record.caseId())
            .param("status", record.status().name())
            .param("reportJson", record.reportJson())
            .param("model", record.model())
            .param("startedAt", Timestamp.from(started))
            .param("finishedAt", finishedTs)
            .update();

        log.debug("Inserted investigation: id={}, caseId={}, status={}", record.id(), record.caseId(), record.status());
    }

    public void updateStatusAndReport(String id, InvestigationStatus status, String reportJson, Instant finishedAt) {
        String sql = """
            UPDATE investigations
            SET status = :status,
                report_json = CAST(:reportJson AS jsonb),
                finished_at = :finishedAt
            WHERE id = :id
            """;

        Timestamp finishedTs = finishedAt != null ? Timestamp.from(finishedAt) : Timestamp.from(Instant.now());

        jdbcClient.sql(sql)
            .param("id", id)
            .param("status", status.name())
            .param("reportJson", reportJson)
            .param("finishedAt", finishedTs)
            .update();

        log.debug("Updated investigation: id={}, status={}", id, status);
    }

    private static class InvestigationRowMapper implements RowMapper<InvestigationRecord> {
        @Override
        public InvestigationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp startedTs = rs.getTimestamp("started_at");
            Timestamp finishedTs = rs.getTimestamp("finished_at");

            return new InvestigationRecord(
                rs.getString("id"),
                rs.getString("case_id"),
                InvestigationStatus.valueOf(rs.getString("status")),
                rs.getString("report_json"),
                rs.getString("model"),
                startedTs != null ? startedTs.toInstant() : null,
                finishedTs != null ? finishedTs.toInstant() : null
            );
        }
    }
}

