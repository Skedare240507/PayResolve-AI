package com.payresolve.ingest;

import com.payresolve.ingest.dto.EventStatsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Data access operations for durable webhook_events inbox.
 */
@Repository
public class WebhookEventRepository {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventRepository.class);

    private static final String SELECT_COLUMNS = """
        id, paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
        received_at, processed_at, last_error, delivery_count, last_received_at, event_created_at,
        custom_id, paypal_order_id, next_attempt_at, payload_mismatch
        """;

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final boolean isPostgres;

    private final RowMapper<WebhookEvent> rowMapper = new WebhookEventRowMapper();

    public record WebhookUpsertResult(Long id, int deliveryCount, boolean upgradedFromUnverified, boolean payloadMismatch) {}

    public WebhookEventRepository(JdbcClient jdbcClient, Clock clock, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        boolean pg = true;
        try (Connection conn = dataSource.getConnection()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            pg = dbName != null && dbName.toLowerCase().contains("postgres");
        } catch (Exception e) {
            log.warn("Could not determine database product name, defaulting isPostgres to true: {}", e.getMessage());
        }
        this.isPostgres = pg;
    }

    public Optional<WebhookEvent> findByPaypalEventId(String paypalEventId) {
        Objects.requireNonNull(paypalEventId, "paypalEventId must not be null");
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM webhook_events WHERE paypal_event_id = :paypalEventId")
            .param("paypalEventId", paypalEventId)
            .query(rowMapper)
            .optional();
    }

    public Optional<WebhookEvent> findById(Long id) {
        Objects.requireNonNull(id, "id must not be null");
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM webhook_events WHERE id = :id")
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public List<WebhookEvent> findAll(int limit, int offset) {
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM webhook_events ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset")
            .param("limit", limit)
            .param("offset", offset)
            .query(rowMapper)
            .list();
    }

    public int countAll() {
        return jdbcClient.sql("SELECT COUNT(*) FROM webhook_events")
            .query(Integer.class)
            .single();
    }

    public long countVerifiedDuplicateDeliveriesBlocked() {
        Long count = jdbcClient.sql("""
            SELECT COALESCE(SUM(CASE WHEN verified = true AND delivery_count > 1 THEN delivery_count - 1 ELSE 0 END), 0)
            FROM webhook_events
            """)
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    public Optional<Instant> findLastReceivedAt() {
        return jdbcClient.sql("""
            SELECT COALESCE(last_received_at, received_at) AS ts
            FROM webhook_events
            ORDER BY COALESCE(last_received_at, received_at) DESC
            LIMIT 1
            """)
            .query((rs, n) -> rs.getTimestamp("ts").toInstant())
            .optional();
    }

    /**
     * Finds webhook events matching a shop order strictly via indexed columns
     * (custom_id, paypal_order_id, resource_id), never via raw_body LIKE substring matching.
     */
    public List<WebhookEvent> findByOrderRefs(String shopOrderId, String paypalOrderId, String captureId) {
        List<String> clauses = new java.util.ArrayList<>();
        if (shopOrderId != null && !shopOrderId.isBlank()) {
            clauses.add("custom_id = :shopOrderId");
        }
        if (paypalOrderId != null && !paypalOrderId.isBlank()) {
            clauses.add("paypal_order_id = :paypalOrderId");
            clauses.add("resource_id = :paypalOrderId");
        }
        if (captureId != null && !captureId.isBlank()) {
            clauses.add("resource_id = :captureId");
        }
        if (clauses.isEmpty()) {
            return List.of();
        }

        String sql = "SELECT " + SELECT_COLUMNS + " FROM webhook_events WHERE "
            + String.join(" OR ", clauses) + " ORDER BY received_at ASC, id ASC";

        var spec = jdbcClient.sql(sql);
        if (shopOrderId != null && !shopOrderId.isBlank()) {
            spec = spec.param("shopOrderId", shopOrderId);
        }
        if (paypalOrderId != null && !paypalOrderId.isBlank()) {
            spec = spec.param("paypalOrderId", paypalOrderId);
        }
        if (captureId != null && !captureId.isBlank()) {
            spec = spec.param("captureId", captureId);
        }
        return spec.query(rowMapper).list();
    }

    /**
     * Poisoning-resistant upsert for incoming webhooks:
     * - Unverified deliveries never mutate an existing verified row, never increment delivery_count,
     *   and never create PENDING work.
     * - Verified deliveries upgrade any existing unverified row in place (verified=true, status=PENDING,
     *   verified body, delivery_count=1).
     * - Verified duplicate deliveries increment delivery_count and detect conflicting payloads.
     */
    public WebhookUpsertResult upsert(
        String paypalEventId,
        String eventType,
        String resourceId,
        String rawBody,
        String headersJson,
        boolean verified,
        String status,
        Instant eventCreatedAt,
        String customId,
        String paypalOrderId
    ) {
        Objects.requireNonNull(paypalEventId, "paypalEventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(rawBody, "rawBody must not be null");

        Instant now = clock.instant();
        Optional<WebhookEvent> existingOpt = findByPaypalEventId(paypalEventId);

        if (existingOpt.isPresent()) {
            WebhookEvent existing = existingOpt.get();

            // Unverified deliveries NEVER modify an existing verified row and NEVER increment delivery_count
            if (!verified) {
                return new WebhookUpsertResult(existing.id(), existing.deliveryCount(), false, existing.payloadMismatch());
            }

            // Upgrade an unverified placeholder row cleanly in place to a verified PENDING row
            if (!existing.verified()) {
                String sql = isPostgres
                    ? """
                        UPDATE webhook_events
                        SET event_type = :eventType,
                            resource_id = :resourceId,
                            raw_body = :rawBody,
                            headers_json = CAST(:headersJson AS jsonb),
                            verified = true,
                            status = 'PENDING',
                            attempts = 0,
                            delivery_count = 1,
                            last_error = NULL,
                            last_received_at = :now,
                            event_created_at = :eventCreatedAt,
                            custom_id = :customId,
                            paypal_order_id = :paypalOrderId,
                            next_attempt_at = NULL,
                            payload_mismatch = false
                        WHERE id = :id
                        """
                    : """
                        UPDATE webhook_events
                        SET event_type = :eventType,
                            resource_id = :resourceId,
                            raw_body = :rawBody,
                            headers_json = :headersJson,
                            verified = true,
                            status = 'PENDING',
                            attempts = 0,
                            delivery_count = 1,
                            last_error = NULL,
                            last_received_at = :now,
                            event_created_at = :eventCreatedAt,
                            custom_id = :customId,
                            paypal_order_id = :paypalOrderId,
                            next_attempt_at = NULL,
                            payload_mismatch = false
                        WHERE id = :id
                        """;
                jdbcClient.sql(sql)
                    .param("id", existing.id())
                    .param("eventType", eventType)
                    .param("resourceId", resourceId)
                    .param("rawBody", rawBody)
                    .param("headersJson", headersJson)
                    .param("now", Timestamp.from(now))
                    .param("eventCreatedAt", eventCreatedAt != null ? Timestamp.from(eventCreatedAt) : null)
                    .param("customId", customId)
                    .param("paypalOrderId", paypalOrderId)
                    .update();
                return new WebhookUpsertResult(existing.id(), 1, true, false);
            }

            // Verified duplicate delivery: increment delivery_count and detect payload mismatch
            boolean mismatch = existing.payloadMismatch() || !Objects.equals(normalizeBody(existing.rawBody()), normalizeBody(rawBody));
            int nextDeliveryCount = existing.deliveryCount() + 1;
            jdbcClient.sql("""
                UPDATE webhook_events
                SET delivery_count = :deliveryCount,
                    last_received_at = :lastReceivedAt,
                    payload_mismatch = :payloadMismatch
                WHERE id = :id
                """)
                .param("deliveryCount", nextDeliveryCount)
                .param("lastReceivedAt", Timestamp.from(now))
                .param("payloadMismatch", mismatch)
                .param("id", existing.id())
                .update();

            return new WebhookUpsertResult(existing.id(), nextDeliveryCount, false, mismatch);
        }

        // Insert new row (verified deliveries only enter normal inbox flow; unverified only if directly called)
        String effectiveStatus = verified ? "PENDING" : status;
        if (isPostgres) {
            String sql = """
                INSERT INTO webhook_events (
                    paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                    delivery_count, received_at, last_received_at, event_created_at, custom_id, paypal_order_id,
                    next_attempt_at, payload_mismatch
                ) VALUES (
                    :paypalEventId, :eventType, :resourceId, :rawBody, CAST(:headersJson AS jsonb), :verified, :status, 0,
                    :deliveryCount, :receivedAt, :lastReceivedAt, :eventCreatedAt, :customId, :paypalOrderId,
                    NULL, false
                )
                RETURNING id, delivery_count
                """;
            return jdbcClient.sql(sql)
                .param("paypalEventId", paypalEventId)
                .param("eventType", eventType)
                .param("resourceId", resourceId)
                .param("rawBody", rawBody)
                .param("headersJson", headersJson)
                .param("verified", verified)
                .param("status", effectiveStatus)
                .param("deliveryCount", verified ? 1 : 0)
                .param("receivedAt", Timestamp.from(now))
                .param("lastReceivedAt", Timestamp.from(now))
                .param("eventCreatedAt", eventCreatedAt != null ? Timestamp.from(eventCreatedAt) : null)
                .param("customId", customId)
                .param("paypalOrderId", paypalOrderId)
                .query((rs, rowNum) -> new WebhookUpsertResult(rs.getLong("id"), rs.getInt("delivery_count"), false, false))
                .single();
        } else {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcClient.sql("""
                INSERT INTO webhook_events (
                    paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                    delivery_count, received_at, last_received_at, event_created_at, custom_id, paypal_order_id,
                    next_attempt_at, payload_mismatch
                ) VALUES (
                    :paypalEventId, :eventType, :resourceId, :rawBody, :headersJson, :verified, :status, 0,
                    :deliveryCount, :receivedAt, :lastReceivedAt, :eventCreatedAt, :customId, :paypalOrderId,
                    NULL, false
                )
                """)
                .param("paypalEventId", paypalEventId)
                .param("eventType", eventType)
                .param("resourceId", resourceId)
                .param("rawBody", rawBody)
                .param("headersJson", headersJson)
                .param("verified", verified)
                .param("status", effectiveStatus)
                .param("deliveryCount", verified ? 1 : 0)
                .param("receivedAt", Timestamp.from(now))
                .param("lastReceivedAt", Timestamp.from(now))
                .param("eventCreatedAt", eventCreatedAt != null ? Timestamp.from(eventCreatedAt) : null)
                .param("customId", customId)
                .param("paypalOrderId", paypalOrderId)
                .update(keyHolder, "id");
            long id = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : 1L;
            return new WebhookUpsertResult(id, verified ? 1 : 0, false, false);
        }
    }

    private String normalizeBody(String body) {
        return body == null ? "" : body.replaceAll("\\s+", "");
    }

    public Long insert(
        String paypalEventId,
        String eventType,
        String resourceId,
        String rawBody,
        String headersJson,
        boolean verified,
        String status
    ) {
        return upsert(paypalEventId, eventType, resourceId, rawBody, headersJson, verified, status, null, null, null).id();
    }

    /**
     * Claims PENDING, verified webhook rows eligible for processing now with FOR UPDATE SKIP LOCKED.
     */
    public List<WebhookEvent> claimPendingEvents(int limit) {
        Timestamp nowTs = Timestamp.from(clock.instant());
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + """
            FROM webhook_events
            WHERE status = 'PENDING'
              AND verified = true
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY received_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
            .param("now", nowTs)
            .param("limit", limit)
            .query(rowMapper)
            .list();
    }

    public void markProcessed(Long id, Instant processedAt) {
        jdbcClient.sql("""
            UPDATE webhook_events
            SET status = 'PROCESSED', processed_at = :processedAt, attempts = attempts + 1,
                last_error = NULL, next_attempt_at = NULL
            WHERE id = :id
            """)
            .param("id", id)
            .param("processedAt", Timestamp.from(processedAt))
            .update();
    }

    /**
     * Re-queues an arrival-race webhook cleanly without incrementing attempts or marking failed.
     */
    public void requeueForArrivalRace(Long id, Instant nextAttemptAt, String note) {
        jdbcClient.sql("""
            UPDATE webhook_events
            SET status = 'PENDING',
                next_attempt_at = :nextAttemptAt,
                last_error = :note
            WHERE id = :id
            """)
            .param("id", id)
            .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
            .param("note", note)
            .update();
    }

    public void recordAttemptFailure(Long id, String errorMessage, boolean willRetry) {
        recordAttemptFailure(id, errorMessage, willRetry, clock.instant());
    }

    public void recordAttemptFailure(Long id, String errorMessage, boolean willRetry, Instant attemptedAt) {
        String newStatus = willRetry ? "PENDING" : "FAILED";
        jdbcClient.sql("""
            UPDATE webhook_events
            SET status = :status, attempts = attempts + 1, last_error = :errorMessage,
                processed_at = :attemptedAt, next_attempt_at = NULL
            WHERE id = :id
            """)
            .param("id", id)
            .param("status", newStatus)
            .param("errorMessage", errorMessage)
            .param("attemptedAt", Timestamp.from(attemptedAt))
            .update();
    }

    public EventStatsDto getStats() {
        return jdbcClient.sql("""
            SELECT
                COUNT(*) AS total_received,
                COUNT(CASE WHEN verified = true THEN 1 END) AS total_verified,
                COUNT(CASE WHEN status = 'PROCESSED' THEN 1 END) AS total_processed,
                COUNT(CASE WHEN status IN ('FAILED', 'DUPLICATE', 'UNVERIFIED') THEN 1 END) AS total_failed_or_duplicate
            FROM webhook_events
            """)
            .query((rs, rowNum) -> new EventStatsDto(
                rs.getLong("total_received"),
                rs.getLong("total_verified"),
                rs.getLong("total_processed"),
                rs.getLong("total_failed_or_duplicate")
            ))
            .single();
    }

    public void updateStatus(Long id, String status) {
        jdbcClient.sql("""
            UPDATE webhook_events
            SET status = :status
            WHERE id = :id
            """)
            .param("id", id)
            .param("status", status)
            .update();
    }

    private static class WebhookEventRowMapper implements RowMapper<WebhookEvent> {
        @Override
        public WebhookEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp receivedAt = rs.getTimestamp("received_at");
            Timestamp processedAt = rs.getTimestamp("processed_at");
            Timestamp lastReceivedAt = rs.getTimestamp("last_received_at");
            Timestamp eventCreatedAt = rs.getTimestamp("event_created_at");
            Timestamp nextAttemptAt = rs.getTimestamp("next_attempt_at");
            return new WebhookEvent(
                rs.getLong("id"),
                rs.getString("paypal_event_id"),
                rs.getString("event_type"),
                rs.getString("resource_id"),
                rs.getString("raw_body"),
                rs.getString("headers_json"),
                rs.getBoolean("verified"),
                rs.getString("status"),
                rs.getInt("attempts"),
                receivedAt != null ? receivedAt.toInstant() : null,
                processedAt != null ? processedAt.toInstant() : null,
                rs.getString("last_error"),
                rs.getInt("delivery_count"),
                lastReceivedAt != null ? lastReceivedAt.toInstant() : null,
                eventCreatedAt != null ? eventCreatedAt.toInstant() : null,
                rs.getString("custom_id"),
                rs.getString("paypal_order_id"),
                nextAttemptAt != null ? nextAttemptAt.toInstant() : null,
                rs.getBoolean("payload_mismatch")
            );
        }
    }
}
