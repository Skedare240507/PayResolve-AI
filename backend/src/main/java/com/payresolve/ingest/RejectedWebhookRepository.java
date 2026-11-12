package com.payresolve.ingest;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Data access for quarantined unverified webhook attempts in {@code rejected_webhooks}.
 */
@Repository
public class RejectedWebhookRepository {

    private final JdbcClient jdbcClient;

    public RejectedWebhookRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(Instant receivedAt, String paypalEventId, String reason, String bodySha256, String ip) {
        jdbcClient.sql("""
            INSERT INTO rejected_webhooks (received_at, paypal_event_id, reason, body_sha256, ip)
            VALUES (:receivedAt, :paypalEventId, :reason, :bodySha256, :ip)
            """)
            .param("receivedAt", Timestamp.from(receivedAt))
            .param("paypalEventId", paypalEventId)
            .param("reason", reason)
            .param("bodySha256", bodySha256)
            .param("ip", ip)
            .update();
    }

    public int countAll() {
        return jdbcClient.sql("SELECT COUNT(*) FROM rejected_webhooks")
            .query(Integer.class)
            .single();
    }
}

