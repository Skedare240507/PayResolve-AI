package com.payresolve.shop;

import com.payresolve.common.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fault injection switches for demo purposes.
 * Persisted in PostgreSQL (table `demo_fault_switches`) with thread-safe atomic mirrors
 * and an automatic 10-minute safety reset so active fault switches never linger in live demos.
 */
@Component
public class DemoFaultSwitches {

    public static final Duration AUTO_RESET_DURATION = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(DemoFaultSwitches.class);
    private static final String DEFAULT_ID = "DEFAULT";

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final boolean demoMode;

    private final AtomicBoolean failWebhookHandler = new AtomicBoolean(false);
    private final AtomicBoolean dropWebhook = new AtomicBoolean(false);
    private final AtomicBoolean duplicateDelivery = new AtomicBoolean(false);
    private final AtomicBoolean alterOrderTotal = new AtomicBoolean(false);
    private volatile Instant resetsAt;

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

    @Scheduled(fixedDelay = 15_000)
    public void scheduledAutoResetCheck() {
        enforceAutoResetIfExpired();
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public boolean isFailWebhookHandler() {
        enforceAutoResetIfExpired();
        return demoMode && failWebhookHandler.get();
    }

    public void setFailWebhookHandler(boolean fail) {
        ensureDemoModeEnabled();
        failWebhookHandler.set(fail);
        refreshResetDeadline(fail);
        persistToDatabase();
    }

    public boolean isDropWebhook() {
        enforceAutoResetIfExpired();
        return demoMode && dropWebhook.get();
    }

    public void setDropWebhook(boolean drop) {
        ensureDemoModeEnabled();
        dropWebhook.set(drop);
        refreshResetDeadline(drop);
        persistToDatabase();
    }

    public boolean isDuplicateDelivery() {
        enforceAutoResetIfExpired();
        return demoMode && duplicateDelivery.get();
    }

    public void setDuplicateDelivery(boolean duplicate) {
        ensureDemoModeEnabled();
        duplicateDelivery.set(duplicate);
        refreshResetDeadline(duplicate);
        persistToDatabase();
    }

    public boolean isAlterOrderTotal() {
        enforceAutoResetIfExpired();
        return demoMode && alterOrderTotal.get();
    }

    public void setAlterOrderTotal(boolean alter) {
        ensureDemoModeEnabled();
        alterOrderTotal.set(alter);
        refreshResetDeadline(alter);
        persistToDatabase();
    }

    public synchronized void updateAll(Boolean fail, Boolean drop, Boolean duplicate, Boolean alter) {
        ensureDemoModeEnabled();
        boolean turnedOn = false;
        if (fail != null) {
            failWebhookHandler.set(fail);
            turnedOn |= fail;
        }
        if (drop != null) {
            dropWebhook.set(drop);
            turnedOn |= drop;
        }
        if (duplicate != null) {
            duplicateDelivery.set(duplicate);
            turnedOn |= duplicate;
        }
        if (alter != null) {
            alterOrderTotal.set(alter);
            turnedOn |= alter;
        }
        refreshResetDeadline(turnedOn);
        persistToDatabase();
    }

    public Instant getResetsAt() {
        enforceAutoResetIfExpired();
        return anyActive() ? resetsAt : null;
    }

    public List<String> getActiveFaultNames() {
        enforceAutoResetIfExpired();
        if (!demoMode) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (failWebhookHandler.get()) names.add("Fail webhook handler");
        if (dropWebhook.get()) names.add("Drop webhook");
        if (alterOrderTotal.get()) names.add("Alter order total");
        if (duplicateDelivery.get()) names.add("Duplicate delivery");
        return names;
    }

    public boolean anyActive() {
        return failWebhookHandler.get()
            || dropWebhook.get()
            || duplicateDelivery.get()
            || alterOrderTotal.get();
    }

    public synchronized void enforceAutoResetIfExpired() {
        if (!anyActive()) {
            resetsAt = null;
            return;
        }
        if (resetsAt != null && !clock.instant().isBefore(resetsAt)) {
            log.info("Auto-resetting demo fault switches after 10-minute safety window");
            failWebhookHandler.set(false);
            dropWebhook.set(false);
            duplicateDelivery.set(false);
            alterOrderTotal.set(false);
            resetsAt = null;
            persistToDatabase();
        }
    }

    public synchronized void reset() {
        ensureDemoModeEnabled();
        failWebhookHandler.set(false);
        dropWebhook.set(false);
        duplicateDelivery.set(false);
        alterOrderTotal.set(false);
        resetsAt = null;
        persistToDatabase();
        log.info("Reset all demo fault switches to false in memory and database");
    }

    private synchronized void refreshResetDeadline(boolean enabledNow) {
        if (!anyActive()) {
            resetsAt = null;
        } else if (enabledNow || resetsAt == null) {
            resetsAt = clock.instant().plus(AUTO_RESET_DURATION);
        }
    }

    private void ensureDemoModeEnabled() {
        if (!demoMode) {
            throw new ValidationException("Fault switches are demo-only and disabled when payresolve.demo-mode is false");
        }
    }

    public synchronized void loadFromDatabase() {
        try {
            jdbcClient.sql("""
                SELECT fail_webhook_handler, drop_webhook, duplicate_delivery, alter_order_total, updated_at
                FROM demo_fault_switches
                WHERE id = :id
                """)
                .param("id", DEFAULT_ID)
                .query((rs, rowNum) -> {
                    failWebhookHandler.set(rs.getBoolean("fail_webhook_handler"));
                    dropWebhook.set(rs.getBoolean("drop_webhook"));
                    duplicateDelivery.set(rs.getBoolean("duplicate_delivery"));
                    alterOrderTotal.set(rs.getBoolean("alter_order_total"));
                    Timestamp updatedTs = rs.getTimestamp("updated_at");
                    if (anyActive()) {
                        Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : clock.instant();
                        resetsAt = updatedAt.plus(AUTO_RESET_DURATION);
                    } else {
                        resetsAt = null;
                    }
                    return null;
                })
                .optional();
            enforceAutoResetIfExpired();
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
