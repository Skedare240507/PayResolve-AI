package com.payresolve.shop;

import com.payresolve.common.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fault injection switches for demo purposes.
 * Persisted in the database (table `demo_fault_switches`) with thread-safe cached atomic booleans.
 * Guarded by the `payresolve.demo-mode` configuration flag.
 */
@Component
public class DemoFaultSwitches {

    private static final Logger log = LoggerFactory.getLogger(DemoFaultSwitches.class);
    private static final String DEFAULT_ID = "DEFAULT";

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final boolean demoMode;

    private final AtomicBoolean failWebhookHandler = new AtomicBoolean(false);
    private final AtomicBoolean dropWebhook = new AtomicBoolean(false);
    private final AtomicBoolean duplicateDelivery = new AtomicBoolean(false);
    private final AtomicBoolean alterOrderTotal = new AtomicBoolean(false);

    public DemoFaultSwitches(
            JdbcClient jdbcClient,
            Clock clock,
            @Value("${payresolve.demo-mode:true}") boolean demoMode
    ) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.demoMode = demoMode;
    }

    @PostConstruct
    public void init() {
        loadFromDatabase();
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public boolean isFailWebhookHandler() {
        return demoMode && failWebhookHandler.get();
    }

    public void setFailWebhookHandler(boolean fail) {
        ensureDemoModeEnabled();
        failWebhookHandler.set(fail);
        persistToDatabase();
    }

    public boolean isDropWebhook() {
        return demoMode && dropWebhook.get();
    }

    public void setDropWebhook(boolean drop) {
        ensureDemoModeEnabled();
        dropWebhook.set(drop);
        persistToDatabase();
    }

    public boolean isDuplicateDelivery() {
        return demoMode && duplicateDelivery.get();
    }

    public void setDuplicateDelivery(boolean duplicate) {
        ensureDemoModeEnabled();
        duplicateDelivery.set(duplicate);
        persistToDatabase();
    }

    public boolean isAlterOrderTotal() {
        return demoMode && alterOrderTotal.get();
    }

    public void setAlterOrderTotal(boolean alter) {
        ensureDemoModeEnabled();
        alterOrderTotal.set(alter);
        persistToDatabase();
    }

    public void reset() {
        ensureDemoModeEnabled();
        failWebhookHandler.set(false);
        dropWebhook.set(false);
        duplicateDelivery.set(false);
        alterOrderTotal.set(false);
        persistToDatabase();
        log.info("Reset all demo fault switches to false in memory and database");
    }

    private void ensureDemoModeEnabled() {
        if (!demoMode) {
            throw new ValidationException("Fault switches are demo-only and disabled when payresolve.demo-mode is false");
        }
    }

    public synchronized void loadFromDatabase() {
        try {
            jdbcClient.sql("""
                SELECT fail_webhook_handler, drop_webhook, duplicate_delivery, alter_order_total
                FROM demo_fault_switches
                WHERE id = :id
                """)
                .param("id", DEFAULT_ID)
                .query((rs, rowNum) -> {
                    failWebhookHandler.set(rs.getBoolean("fail_webhook_handler"));
                    dropWebhook.set(rs.getBoolean("drop_webhook"));
                    duplicateDelivery.set(rs.getBoolean("duplicate_delivery"));
                    alterOrderTotal.set(rs.getBoolean("alter_order_total"));
                    return null;
                })
                .optional();
            log.info("Loaded demo fault switches from database: failHandler={}, dropWebhook={}, duplicateDelivery={}, alterOrderTotal={}",
                failWebhookHandler.get(), dropWebhook.get(), duplicateDelivery.get(), alterOrderTotal.get());
        } catch (Exception e) {
            log.warn("Could not load demo fault switches from database (table may not be initialized yet): {}", e.getMessage());
        }
    }

    public synchronized void persistToDatabase() {
        Instant now = clock.instant();
        try {
            int updated = jdbcClient.sql("""
                UPDATE demo_fault_switches
                SET fail_webhook_handler = :fail,
                    drop_webhook = :drop,
                    duplicate_delivery = :dup,
                    alter_order_total = :alter,
                    updated_at = :updatedAt
                WHERE id = :id
                """)
                .param("fail", failWebhookHandler.get())
                .param("drop", dropWebhook.get())
                .param("dup", duplicateDelivery.get())
                .param("alter", alterOrderTotal.get())
                .param("updatedAt", Timestamp.from(now))
                .param("id", DEFAULT_ID)
                .update();

            if (updated == 0) {
                // Row doesn't exist yet, insert it
                jdbcClient.sql("""
                    INSERT INTO demo_fault_switches (id, fail_webhook_handler, drop_webhook, alter_order_total, duplicate_delivery, updated_at)
                    VALUES (:id, :fail, :drop, :alter, :dup, :updatedAt)
                    """)
                    .param("id", DEFAULT_ID)
                    .param("fail", failWebhookHandler.get())
                    .param("drop", dropWebhook.get())
                    .param("alter", alterOrderTotal.get())
                    .param("dup", duplicateDelivery.get())
                    .param("updatedAt", Timestamp.from(now))
                    .update();
            }
        } catch (Exception e) {
            log.warn("Could not persist demo fault switches to database: {}", e.getMessage());
        }
    }
}
