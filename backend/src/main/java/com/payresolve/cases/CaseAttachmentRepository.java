package com.payresolve.cases;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class CaseAttachmentRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CaseAttachmentRecord> rowMapper = (rs, rowNum) -> new CaseAttachmentRecord(
        rs.getString("id"),
        rs.getString("case_id"),
        rs.getString("filename"),
        rs.getString("content_type"),
        rs.getLong("file_size"),
        rs.getString("data_base64"),
        rs.getString("extracted_data"),
        rs.getTimestamp("created_at").toInstant()
    );

    public CaseAttachmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(CaseAttachmentRecord record) {
        String sql = """
            INSERT INTO case_attachments (id, case_id, filename, content_type, file_size, data_base64, extracted_data, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """;
        jdbcTemplate.update(
            sql,
            record.id(),
            record.caseId(),
            record.filename(),
            record.contentType(),
            record.fileSize(),
            record.dataBase64(),
            record.extractedData() != null ? record.extractedData() : "{}",
            Timestamp.from(record.createdAt())
        );
    }

    public void updateExtractedData(String id, String extractedJson) {
        String sql = "UPDATE case_attachments SET extracted_data = ?::jsonb WHERE id = ?";
        jdbcTemplate.update(sql, extractedJson != null ? extractedJson : "{}", id);
    }

    public List<CaseAttachmentRecord> findByCaseId(String caseId) {
        String sql = "SELECT * FROM case_attachments WHERE case_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, caseId);
    }

    public Optional<CaseAttachmentRecord> findById(String id) {
        String sql = "SELECT * FROM case_attachments WHERE id = ?";
        List<CaseAttachmentRecord> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public int countByCaseId(String caseId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM case_attachments WHERE case_id = ?",
            Integer.class,
            caseId
        );
        return count != null ? count : 0;
    }
}

