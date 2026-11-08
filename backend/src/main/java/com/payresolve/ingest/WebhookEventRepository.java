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

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final boolean isPostgres;

    private final RowMapper<WebhookEvent> rowMapper = new WebhookEventRowMapper();

    public record WebhookUpsertResult(Long id, int deliveryCount) {}

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
        return jdbcClient.sql("""
            SELECT id, paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                   received_at, processed_at, last_error, delivery_count, last_received_at, event_created_at, custom_id, paypal_order_id
            FROM webhook_events
            WHERE paypal_event_id = :paypalEventId
            """)
            .param("paypalEventId", paypalEventId)
            .query(rowMapper)
            .optional();
    }

    public Optional<WebhookEvent> findById(Long id) {
        Objects.requireNonNull(id, "id must not be null");
        return jdbcClient.sql("""
            SELECT id, paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                   received_at, processed_at, last_error, delivery_count, last_received_at, event_created_at, custom_id, paypal_order_id
            FROM webhook_events
            WHERE id = :id
            """)
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public List<WebhookEvent> findAll(int limit, int offset) {
        return jdbcClient.sql("""
            SELECT id, paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                   received_at, processed_at, last_error, delivery_count, last_received_at, event_created_at, custom_id, paypal_order_id
            FROM webhook_events
            ORDER BY received_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
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

    /**
     * Inserts or updates incoming webhook event on conflict (paypal_event_id).
     * If already present, increments delivery_count and updates last_received_at.
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

        if (isPostgres) {
            String sql = """
                INSERT INTO webhook_events (
                    paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                    delivery_count, received_at, last_received_at, event_created_at, custom_id, paypal_order_id
                ) VALUES (
                    :paypalEventId, :eventType, :resourceId, :rawBody, CAST(:headersJson AS jsonb), :verified, :status, 0,
                    1, :receivedAt, :lastReceivedAt, :eventCreatedAt, :customId, :paypalOrderId
                )
                ON CONFLICT (paypal_event_id) DO UPDATE SET
                    delivery_count = webhook_events.delivery_count + 1,
                    last_received_at = CURRENT_TIMESTAMP
                RETURNING id, delivery_count
                """;

            return jdbcClient.sql(sql)
                .param("paypalEventId", paypalEventId)
                .param("eventType", eventType)
                .param("resourceId", resourceId)
                .param("rawBody", rawBody)
                .param("headersJson", headersJson)
                .param("verified", verified)
                .param("status", status)
                .param("receivedAt", Timestamp.from(now))
                .param("lastReceivedAt", Timestamp.from(now))
                .param("eventCreatedAt", eventCreatedAt != null ? Timestamp.from(eventCreatedAt) : null)
                .param("customId", customId)
                .param("paypalOrderId", paypalOrderId)
                .query((rs, rowNum) -> new WebhookUpsertResult(rs.getLong("id"), rs.getInt("delivery_count")))
                .single();
        } else {
            // Portable logic for H2 test environment
            Optional<WebhookEvent> existing = findByPaypalEventId(paypalEventId);
            if (existing.isPresent()) {
                WebhookEvent ev = existing.get();
                int newDeliveryCount = ev.deliveryCount() + 1;
                jdbcClient.sql("""
                    UPDATE webhook_events
                    SET delivery_count = :deliveryCount,
                        last_received_at = :lastReceivedAt
                    WHERE id = :id
                    """)
                    .param("deliveryCount", newDeliveryCount)
                    .param("lastReceivedAt", Timestamp.from(now))
                    .param("id", ev.id())
                    .update();
                return new WebhookUpsertResult(ev.id(), newDeliveryCount);
            } else {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcClient.sql("""
                    INSERT INTO webhook_events (
                        paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                        delivery_count, received_at, last_received_at, event_created_at, custom_id, paypal_order_id
                    ) VALUES (
                        :paypalEventId, :eventType, :resourceId, :rawBody, :headersJson, :verified, :status, 0,
                        1, :receivedAt, :lastReceivedAt, :eventCreatedAt, :customId, :paypalOrderId
                    )
                    """)
                    .param("paypalEventId", paypalEventId)
                    .param("eventType", eventType)
                    .param("resourceId", resourceId)
                    .param("rawBody", rawBody)
                    .param("headersJson", headersJson)
                    .param("verified", verified)
                    .param("status", status)
                    .param("receivedAt", Timestamp.from(now))
                    .param("lastReceivedAt", Timestamp.from(now))
                    .param("eventCreatedAt", eventCreatedAt != null ? Timestamp.from(eventCreatedAt) : null)
                    .param("customId", customId)
                    .param("paypalOrderId", paypalOrderId)
                    .update(keyHolder, "id");
                long id = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : 1L;
                return new WebhookUpsertResult(id, 1);
            }
        }
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
     * Claims PENDING, verified webhook rows with FOR UPDATE SKIP LOCKED.
     * Prevents race conditions across parallel worker instances or threads.
     */
    public List<WebhookEvent> claimPendingEvents(int limit) {
        return jdbcClient.sql("""
            SELECT id, paypal_event_id, event_type, resource_id, raw_body, headers_json, verified, status, attempts,
                   received_at, processed_at, last_error, delivery_count, last_received_at, event_created_at, custom_id, paypal_order_id
            FROM webhook_events
            WHERE status = 'PENDING' AND verified = true
            ORDER BY received_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
            .param("limit", limit)
            .query(rowMapper)
            .list();
    }

    public void markProcessed(Long id, Instant processedAt) {
        jdbcClient.sql("""
            UPDATE webhook_events
            SET status = 'PROCESSED', processed_at = :processedAt, attempts = attempts + 1, last_error = NULL
            WHERE id = :id
            """)
            .param("id", id)
            .param("processedAt", Timestamp.from(processedAt))
            .update();
    }

    public void recordAttemptFailure(Long id, String errorMessage, boolean willRetry) {
        recordAttemptFailure(id, errorMessage, willRetry, clock.instant());
    }

    public void recordAttemptFailure(Long id, String errorMessage, boolean willRetry, Instant attemptedAt) {
        String newStatus = willRetry ? "PENDING" : "FAILED";
        jdbcClient.sql("""
            UPDATE webhook_events
            SET status = :status, attempts = attempts + 1, last_error = :errorMessage, processed_at = :attemptedAt
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
                rs.getString("paypal_order_id")
            );
        }
    }
}
