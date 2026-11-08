package com.payresolve.evidence;

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
 * JDBC data access for normalized case evidence items in `case_evidence`.
 */
@Repository
public class CaseEvidenceRepository {

    private static final Logger log = LoggerFactory.getLogger(CaseEvidenceRepository.class);

    private final JdbcClient jdbcClient;
    private final EvidenceRowMapper rowMapper = new EvidenceRowMapper();

    public CaseEvidenceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<CaseEvidenceRecord> findByCaseId(String caseId) {
        String sql = """
            SELECT id, case_id, kind, source, external_id, payload_json, observed_at
            FROM case_evidence
            WHERE case_id = :caseId
            ORDER BY observed_at ASC
            """;
        return jdbcClient.sql(sql)
            .param("caseId", caseId)
            .query(rowMapper)
            .list();
    }

    public void insert(CaseEvidenceRecord record) {
        String sql = """
            INSERT INTO case_evidence (
                id, case_id, kind, source, external_id, payload_json, observed_at
            ) VALUES (
                :id, :caseId, :kind, :source, :externalId, CAST(:payloadJson AS jsonb), :observedAt
            )
            """;

        Instant observed = record.observedAt() != null ? record.observedAt() : Instant.now();

        jdbcClient.sql(sql)
            .param("id", record.id())
            .param("caseId", record.caseId())
            .param("kind", record.kind().name())
            .param("source", record.source().name())
            .param("externalId", record.externalId())
            .param("payloadJson", record.payloadJson() != null ? record.payloadJson() : "{}")
            .param("observedAt", Timestamp.from(observed))
            .update();

        log.debug("Inserted evidence item: id={}, caseId={}, kind={}", record.id(), record.caseId(), record.kind());
    }

    public void deleteByCaseId(String caseId) {
        String sql = "DELETE FROM case_evidence WHERE case_id = :caseId";
        jdbcClient.sql(sql)
            .param("caseId", caseId)
            .update();
    }

    private static class EvidenceRowMapper implements RowMapper<CaseEvidenceRecord> {
        @Override
        public CaseEvidenceRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp observedTs = rs.getTimestamp("observed_at");
            return new CaseEvidenceRecord(
                rs.getString("id"),
                rs.getString("case_id"),
                EvidenceKind.fromString(rs.getString("kind")),
                EvidenceSource.fromString(rs.getString("source")),
                rs.getString("external_id"),
                rs.getString("payload_json"),
                observedTs != null ? observedTs.toInstant() : null
            );
        }
    }
}

