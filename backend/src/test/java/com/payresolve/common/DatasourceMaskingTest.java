package com.payresolve.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceMaskingTest {

    @BeforeEach
    void setUp() {
        SensitiveDataMasker.clearRegisteredValuesForTest();
        SensitiveDataMasker.registerSensitiveValue("jdbc:postgresql://secret-host.pooler.supabase.com:5432/postgres?sslmode=require");
        SensitiveDataMasker.registerSensitiveValue("postgres.my_secret_user");
        SensitiveDataMasker.registerSensitiveValue("SuperSecretPass123!");
    }

    @Test
    @DisplayName("Should mask exact registered DATABASE_URL, DB_USER and DB_PASSWORD")
    void shouldMaskExactRegisteredSecrets() {
        String logLine = "Connecting to jdbc:postgresql://secret-host.pooler.supabase.com:5432/postgres?sslmode=require with user postgres.my_secret_user and pass SuperSecretPass123!";
        String masked = SensitiveDataMasker.mask(logLine);

        assertThat(masked)
            .doesNotContain("secret-host.pooler.supabase.com")
            .doesNotContain("postgres.my_secret_user")
            .doesNotContain("SuperSecretPass123!")
            .contains("[MASKED]");
    }

    @Test
    @DisplayName("Should mask embedded credentials in connection string")
    void shouldMaskEmbeddedCredentialsInUrl() {
        String logLine = "Failed to connect to postgresql://secret_user:secret_pass@aws-0.pooler.supabase.com:5432/postgres";
        String masked = SensitiveDataMasker.mask(logLine);

        assertThat(masked)
            .doesNotContain("secret_user")
            .doesNotContain("secret_pass")
            .contains("[MASKED_USER]:[MASKED_PASSWORD]@");
    }

    @Test
    @DisplayName("Should mask Hikari configuration dumps")
    void shouldMaskHikariConfigDump() {
        String hikariDump = """
            HikariPool-1 - configuration:
            jdbcUrl......................jdbc:postgresql://secret-host.pooler.supabase.com:5432/postgres
            username.....................postgres.my_secret_user
            password.....................<masked>
            """;
        String masked = SensitiveDataMasker.mask(hikariDump);

        assertThat(masked)
            .doesNotContain("secret-host.pooler.supabase.com")
            .doesNotContain("postgres.my_secret_user")
            .contains("jdbcUrl......................[MASKED_DATABASE_URL]")
            .contains("username.....................[MASKED_DB_USER]");
    }

    @Test
    @DisplayName("Should mask PostgreSQL user authentication exception messages")
    void shouldMaskPostgresAuthException() {
        String exceptionMessage = "org.postgresql.util.PSQLException: FATAL: password authentication failed for user \"postgres.my_secret_user\"";
        String masked = SensitiveDataMasker.mask(exceptionMessage);

        assertThat(masked)
            .doesNotContain("postgres.my_secret_user")
            .contains("password authentication failed for user \"[MASKED_USER]\"");
    }

    @Test
    @DisplayName("Should handle null and empty strings safely")
    void shouldHandleNullAndEmpty() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
        assertThat(SensitiveDataMasker.mask("")).isEmpty();
    }
}

