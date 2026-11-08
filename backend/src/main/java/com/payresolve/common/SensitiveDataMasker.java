package com.payresolve.common;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Utility responsible for masking sensitive database configuration (DATABASE_URL,
 * DB_USER, DB_PASSWORD) from all log lines and exception messages.
 */
public final class SensitiveDataMasker {

    private static final Set<String> SENSITIVE_VALUES = ConcurrentHashMap.newKeySet();

    // Mask JDBC PostgreSQL URLs
    private static final Pattern JDBC_POSTGRES_PATTERN = Pattern.compile(
        "jdbc:postgresql://[^\\s\"',;]+",
        Pattern.CASE_INSENSITIVE
    );

    // Mask embedded credentials in URLs (e.g. postgresql://user:pass@host)
    private static final Pattern EMBEDDED_CREDENTIALS_PATTERN = Pattern.compile(
        "((?:jdbc:)?postgresql://)([^:@\\s\"']+):([^@\\s\"']+)@",
        Pattern.CASE_INSENSITIVE
    );

    // Mask Hikari configuration dumps for jdbcUrl
    private static final Pattern HIKARI_JDBC_URL_PATTERN = Pattern.compile(
        "(jdbcUrl\\s*\\.+)[^\\r\\n]+",
        Pattern.CASE_INSENSITIVE
    );

    // Mask Hikari configuration dumps for username
    private static final Pattern HIKARI_USERNAME_PATTERN = Pattern.compile(
        "(username\\s*\\.+)[^\\r\\n]+",
        Pattern.CASE_INSENSITIVE
    );

    // Mask PostgreSQL user authentication error messages
    private static final Pattern POSTGRES_USER_PATTERN = Pattern.compile(
        "(password authentication failed for user \")[^\"]+(\")",
        Pattern.CASE_INSENSITIVE
    );

    static {
        registerFromEnv("DATABASE_URL");
        registerFromEnv("DB_USER");
        registerFromEnv("DB_PASSWORD");
        registerFromEnv("spring.datasource.url");
        registerFromEnv("spring.datasource.username");
        registerFromEnv("spring.datasource.password");
    }

    private SensitiveDataMasker() {}

    public static void registerFromEnv(String propOrEnv) {
        String envVal = System.getenv(propOrEnv);
        if (envVal != null && !envVal.isBlank()) {
            registerSensitiveValue(envVal);
        }
        String sysVal = System.getProperty(propOrEnv);
        if (sysVal != null && !sysVal.isBlank()) {
            registerSensitiveValue(sysVal);
        }
    }

    public static void registerSensitiveValue(String value) {
        if (value != null) {
            String trimmed = value.trim();
            // Don't register empty or trivial values (like "" or "sa")
            if (trimmed.length() >= 3 && !"postgres".equalsIgnoreCase(trimmed)) {
                SENSITIVE_VALUES.add(trimmed);
            }
        }
    }

    public static Set<String> getRegisteredValues() {
        return Collections.unmodifiableSet(SENSITIVE_VALUES);
    }

    public static void clearRegisteredValuesForTest() {
        SENSITIVE_VALUES.clear();
    }

    /**
     * Replaces any occurrence of DATABASE_URL, DB_USER, DB_PASSWORD, embedded credentials,
     * and database connection strings with masked placeholders.
     *
     * @param input the log message or exception trace to mask
     * @return the masked string with sensitive information redacted
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String masked = input;

        // 1. Mask exact registered sensitive values (passwords, usernames, custom URLs)
        for (String secret : SENSITIVE_VALUES) {
            if (secret != null && !secret.isBlank() && masked.contains(secret)) {
                if (secret.startsWith("jdbc:postgresql://") || secret.startsWith("postgresql://")) {
                    masked = masked.replace(secret, "jdbc:postgresql://[MASKED_HOST]/[MASKED_DATABASE]");
                } else {
                    masked = masked.replace(secret, "[MASKED]");
                }
            }
        }

        // 2. Mask embedded user:pass in any PostgreSQL URLs
        masked = EMBEDDED_CREDENTIALS_PATTERN.matcher(masked).replaceAll("$1[MASKED_USER]:[MASKED_PASSWORD]@");

        // 3. Mask any remaining explicit PostgreSQL JDBC URLs
        masked = JDBC_POSTGRES_PATTERN.matcher(masked).replaceAll("jdbc:postgresql://[MASKED_HOST]/[MASKED_DATABASE]");

        // 4. Mask Hikari pool configuration printouts
        masked = HIKARI_JDBC_URL_PATTERN.matcher(masked).replaceAll("$1[MASKED_DATABASE_URL]");
        masked = HIKARI_USERNAME_PATTERN.matcher(masked).replaceAll("$1[MASKED_DB_USER]");

        // 5. Mask PostgreSQL user authentication error messages
        masked = POSTGRES_USER_PATTERN.matcher(masked).replaceAll("$1[MASKED_USER]$2");

        return masked;
    }
}

