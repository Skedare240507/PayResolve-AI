package com.payresolve.cases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * JDBC data access for payment discrepancy cases.
 */
@Repository
public class CaseRepository {

    private static final Logger log = LoggerFactory.getLogger(CaseRepository.class);

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final CaseRowMapper rowMapper = new CaseRowMapper();

    public CaseRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public String nextCaseId() {
        try {
            return jdbcClient.sql("SELECT 'PR-' || lpad(nextval('case_number_seq')::text, 4, '0')")
                .query(String.class)
                .single();
        } catch (Exception e) {
            return jdbcClient.sql("SELECT 'PR-' || lpad(CAST(nextval('case_number_seq') AS VARCHAR), 4, '0')")
                .query(String.class)
                .single();
        }
    }

    public Optional<CaseRecord> findById(String id) {
        String sql = """
            SELECT id, type, status, severity, paypal_order_id, shop_order_id,
                   amount_minor, currency, opened_at, updated_at, resolved_at, summary
            FROM cases
            WHERE id = :id
            """;
        return jdbcClient.sql(sql)
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public Optional<CaseRecord> findByTypeAndPaypalOrderId(CaseType type, String paypalOrderId) {
        String sql = """
            SELECT id, type, status, severity, paypal_order_id, shop_order_id,
                   amount_minor, currency, opened_at, updated_at, resolved_at, summary
            FROM cases
            WHERE type = :type AND paypal_order_id = :paypalOrderId
            """;
        return jdbcClient.sql(sql)
            .param("type", type.name())
            .param("paypalOrderId", paypalOrderId)
            .query(rowMapper)
            .optional();
    }

    public void insert(CaseRecord c) {
        String sql = """
            INSERT INTO cases (
                id, type, status, severity, paypal_order_id, shop_order_id,
                amount_minor, currency, opened_at, updated_at, resolved_at, summary
            ) VALUES (
                :id, :type, :status, :severity, :paypalOrderId, :shopOrderId,
                :amountMinor, :currency, :openedAt, :updatedAt, :resolvedAt, :summary
            )
            """;

        Instant opened = c.openedAt() != null ? c.openedAt() : clock.instant();
        Instant updated = c.updatedAt() != null ? c.updatedAt() : opened;

        jdbcClient.sql(sql)
            .param("id", c.id())
            .param("type", c.type().name())
            .param("status", c.status().name())
            .param("severity", c.severity().name())
            .param("paypalOrderId", c.paypalOrderId())
            .param("shopOrderId", c.shopOrderId())
            .param("amountMinor", c.amountMinor())
            .param("currency", c.currency() != null ? c.currency() : "USD")
            .param("openedAt", Timestamp.from(opened))
            .param("updatedAt", Timestamp.from(updated))
            .param("resolvedAt", c.resolvedAt() != null ? Timestamp.from(c.resolvedAt()) : null)
            .param("summary", c.summary())
            .update();

        log.info("Inserted new case: id={}, type={}, order={}", c.id(), c.type(), c.paypalOrderId());
    }

    public void update(CaseRecord c) {
        String sql = """
            UPDATE cases
            SET severity = :severity,
                shop_order_id = :shopOrderId,
                amount_minor = :amountMinor,
                currency = :currency,
                summary = :summary,
                updated_at = :updatedAt
            WHERE id = :id
            """;

        Instant updated = clock.instant();

        jdbcClient.sql(sql)
            .param("id", c.id())
            .param("severity", c.severity().name())
            .param("shopOrderId", c.shopOrderId())
            .param("amountMinor", c.amountMinor())
            .param("currency", c.currency() != null ? c.currency() : "USD")
            .param("summary", c.summary())
            .param("updatedAt", Timestamp.from(updated))
            .update();

        log.info("Updated existing case: id={}, type={}, order={}", c.id(), c.type(), c.paypalOrderId());
    }

    public boolean updateStatus(String id, CaseStatus newStatus, Instant resolvedAt) {
        String sql = """
            UPDATE cases
            SET status = :status,
                resolved_at = :resolvedAt,
                updated_at = :updatedAt
            WHERE id = :id
            """;

        int updated = jdbcClient.sql(sql)
            .param("id", id)
            .param("status", newStatus.name())
            .param("resolvedAt", resolvedAt != null ? Timestamp.from(resolvedAt) : null)
            .param("updatedAt", Timestamp.from(clock.instant()))
            .update();

        return updated > 0;
    }

    public List<CaseRecord> findAll(String status, String severity, String type, String search, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder sql = new StringBuilder("""
            SELECT id, type, status, severity, paypal_order_id, shop_order_id,
                   amount_minor, currency, opened_at, updated_at, resolved_at, summary
            FROM cases
            WHERE 1=1
            """);

        applyFilters(sql, params, status, severity, type, search);

        sql.append(" ORDER BY opened_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", Math.max(1, limit));
        params.put("offset", Math.max(0, offset));

        var query = jdbcClient.sql(sql.toString());
        params.forEach(query::param);
        return query.query(rowMapper).list();
    }

    public long countAll(String status, String severity, String type, String search) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM cases WHERE 1=1");

        applyFilters(sql, params, status, severity, type, search);

        var query = jdbcClient.sql(sql.toString());
        params.forEach(query::param);
        Long count = query.query(Long.class).single();
        return count != null ? count : 0L;
    }

    private void applyFilters(StringBuilder sql, Map<String, Object> params, String status, String severity, String type, String search) {
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try {
                CaseStatus st = CaseStatus.fromString(status);
                sql.append(" AND status = :status");
                params.put("status", st.name());
            } catch (Exception ignored) {
            }
        }

        if (severity != null && !severity.isBlank() && !"all".equalsIgnoreCase(severity)) {
            try {
                CaseSeverity sev = CaseSeverity.fromString(severity);
                sql.append(" AND severity = :severity");
                params.put("severity", sev.name());
            } catch (Exception ignored) {
            }
        }

        if (type != null && !type.isBlank() && !"all".equalsIgnoreCase(type)) {
            try {
                CaseType t = CaseType.fromString(type);
                sql.append(" AND type = :type");
                params.put("type", t.name());
            } catch (Exception ignored) {
            }
        }

        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(id) LIKE :search OR LOWER(summary) LIKE :search OR LOWER(shop_order_id) LIKE :search OR LOWER(paypal_order_id) LIKE :search)");
            params.put("search", "%" + search.toLowerCase().trim() + "%");
        }
    }

    private static class CaseRowMapper implements RowMapper<CaseRecord> {
        @Override
        public CaseRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp openedTs = rs.getTimestamp("opened_at");
            Timestamp updatedTs = rs.getTimestamp("updated_at");
            Timestamp resolvedTs = rs.getTimestamp("resolved_at");

            long amountMinorVal = rs.getLong("amount_minor");
            Long amountMinor = rs.wasNull() ? null : amountMinorVal;

            return new CaseRecord(
                rs.getString("id"),
                CaseType.fromString(rs.getString("type")),
                CaseStatus.fromString(rs.getString("status")),
                CaseSeverity.fromString(rs.getString("severity")),
                rs.getString("paypal_order_id"),
                rs.getString("shop_order_id"),
                amountMinor,
                rs.getString("currency"),
                openedTs != null ? openedTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null,
                resolvedTs != null ? resolvedTs.toInstant() : null,
                rs.getString("summary")
            );
        }
    }
}
