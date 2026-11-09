package com.payresolve.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that all Flyway database migrations apply cleanly on an empty PostgreSQL database
 * and that all domain tables, constraints, sequences, and triggers are correctly initialized.
 *
 * Uses the running PostgreSQL instance from DATABASE_URL if provided (e.g. CI service container),
 * or dynamically starts a Testcontainer if Docker is available locally.
 */
class FlywayMigrationTest {

    private static PostgreSQLContainer<?> postgresContainer;
    private static String jdbcUrl;
    private static String dbUsername;
    private static String dbPassword;

    @BeforeAll
    static void setUpDatabase() {
        String envDbUrl = System.getenv("DATABASE_URL");
        if (envDbUrl != null && envDbUrl.startsWith("jdbc:postgresql://")) {
            jdbcUrl = envDbUrl;
            dbUsername = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "postgres";
            dbPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "postgres";
        } else {
            try {
                postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("payresolve_test")
                        .withUsername("testuser")
                        .withPassword("testpass");
                postgresContainer.start();
                jdbcUrl = postgresContainer.getJdbcUrl();
                dbUsername = postgresContainer.getUsername();
                dbPassword = postgresContainer.getPassword();
            } catch (Throwable t) {
                Assumptions.assumeTrue(false, "PostgreSQL not available for FlywayMigrationTest: " + t.getMessage());
            }
        }
    }

    @AfterAll
    static void tearDownDatabase() {
        if (postgresContainer != null) {
            try {
                postgresContainer.stop();
            } catch (Exception ignored) {}
        }
    }

    private static Connection getConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
    }

    @Test
    @DisplayName("Should apply V1 to V6 migrations on clean PostgreSQL and verify schema invariants")
    void shouldApplyMigrationsOnCleanPostgres() throws Exception {
        // 1. Run Flyway migrations
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, dbUsername, dbPassword)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(5);

        // 2. Verify all expected tables exist in PostgreSQL information_schema
        Set<String> expectedTables = Set.of(
                "webhook_events",
                "shop_orders",
                "shop_order_items",
                "payments",
                "refunds",
                "shipments",
                "cases",
                "case_evidence",
                "investigations",
                "investigation_steps",
                "proposed_actions",
                "audit_log",
                "users",
                "password_reset_tokens",
                "benchmark_runs",
                "keepalive",
                "products",
                "demo_fault_switches"
        );

        Set<String> actualTables = new HashSet<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {

            while (rs.next()) {
                actualTables.add(rs.getString("table_name"));
            }
        }

        assertThat(actualTables).containsAll(expectedTables);

        // 3. Test keepalive table insert & select
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            int inserted = stmt.executeUpdate("INSERT INTO public.keepalive DEFAULT VALUES");
            assertThat(inserted).isEqualTo(1);

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM public.keepalive")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(1L);
            }
        }

        // 4. Verify unique constraint on webhook_events.paypal_event_id
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO webhook_events (paypal_event_id, event_type, raw_body) VALUES (?, ?, ?)")) {

            ps.setString(1, "WH-UNIQUE-TEST-001");
            ps.setString(2, "PAYMENT.CAPTURE.COMPLETED");
            ps.setString(3, "{}");
            ps.executeUpdate();

            // Duplicate insert must fail with unique constraint violation
            assertThatThrownBy(ps::executeUpdate)
                    .hasMessageContaining("duplicate key value violates unique constraint");
        }

        // 5. Verify unique constraint on proposed_actions.idempotency_key
        try (Connection conn = getConnection()) {
            // First create a case to satisfy foreign key
            try (PreparedStatement psCase = conn.prepareStatement(
                    "INSERT INTO cases (id, type, severity) VALUES ('CASE-TEST-001', 'PAID_NOT_MARKED', 'HIGH')")) {
                psCase.executeUpdate();
            }

            try (PreparedStatement psAction = conn.prepareStatement(
                    "INSERT INTO proposed_actions (case_id, type, idempotency_key, proposed_by) VALUES (?, ?, ?, ?)")) {
                psAction.setString(1, "CASE-TEST-001");
                psAction.setString(2, "SYNC_ORDER_STATUS");
                psAction.setString(3, "IDEMP-KEY-TEST-001");
                psAction.setString(4, "AI");
                psAction.executeUpdate();

                // Duplicate insert must fail with unique constraint violation
                assertThatThrownBy(psAction::executeUpdate)
                        .hasMessageContaining("duplicate key value violates unique constraint");
            }
        }

        // 6. Verify case_number_seq generates PR-xxxx formatted values
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 'PR-' || lpad(nextval('case_number_seq')::text, 4, '0')")) {
            assertThat(rs.next()).isTrue();
            String seqVal = rs.getString(1);
            assertThat(seqVal).matches("^PR-\\d{4}$");
        }

        // 7. Verify audit_log immutability trigger forbids updates and deletes
        try (Connection conn = getConnection()) {
            long auditId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO audit_log (actor, action, subject_type, subject_id) VALUES (?, ?, ?, ?) RETURNING id")) {
                ps.setString(1, "OPERATOR");
                ps.setString(2, "APPROVE_ACTION");
                ps.setString(3, "ACTION");
                ps.setString(4, "1");
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                auditId = rs.getLong(1);
            }

            try (PreparedStatement psUpdate = conn.prepareStatement("UPDATE audit_log SET action = 'TAMPERED' WHERE id = ?")) {
                psUpdate.setLong(1, auditId);
                assertThatThrownBy(psUpdate::executeUpdate)
                        .hasMessageContaining("audit_log is append-only");
            }

            try (PreparedStatement psDelete = conn.prepareStatement("DELETE FROM audit_log WHERE id = ?")) {
                psDelete.setLong(1, auditId);
                assertThatThrownBy(psDelete::executeUpdate)
                        .hasMessageContaining("audit_log is append-only");
            }
        }
    }
}
