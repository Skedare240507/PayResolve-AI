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
    @DisplayName("Should mask Bearer tokens in Authorization headers / logs")
    void shouldMaskBearerTokens() {
        String logLine = "Outbound request with Authorization: Bearer A21AAL_mockSecretTokenValue99887766554433";
        String masked = SensitiveDataMasker.mask(logLine);

        assertThat(masked)
            .doesNotContain("A21AAL_mockSecretTokenValue99887766554433")
            .contains("Authorization: Bearer [MASKED_TOKEN]");
    }

    @Test
    @DisplayName("Should mask Groq and Gemini API keys")
    void shouldMaskProviderApiKeys() {
        String groqLog = "Using Groq key gsk_abcdef1234567890abcdef1234567890 for chat request";
        String geminiLog = "Using Gemini key AIzaSyD12345678901234567890123456789012 for document analysis";

        assertThat(SensitiveDataMasker.mask(groqLog))
            .doesNotContain("gsk_abcdef1234567890abcdef1234567890")
            .contains("[MASKED_API_KEY]");

        assertThat(SensitiveDataMasker.mask(geminiLog))
            .doesNotContain("AIzaSyD12345678901234567890123456789012")
            .contains("[MASKED_API_KEY]");
    }

    @Test
    @DisplayName("Should mask parameter assignments like password=, secret=, client_secret=")
    void shouldMaskSensitiveParamAssignments() {
        String logLine = "PayPal request params: client_id=sandbox_user&client_secret=super_secret_client_val_123&grant_type=client_credentials";
        String masked = SensitiveDataMasker.mask(logLine);

        assertThat(masked)
            .doesNotContain("super_secret_client_val_123")
            .contains("client_secret=[MASKED]");
    }

    @Test
    @DisplayName("Should handle null and empty strings safely")
    void shouldHandleNullAndEmpty() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
        assertThat(SensitiveDataMasker.mask("")).isEmpty();
    }
}

