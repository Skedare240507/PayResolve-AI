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

    // Mask Bearer tokens in Authorization headers / logs
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
        "(Bearer\\s+)[A-Za-z0-9_\\-\\.]+",
        Pattern.CASE_INSENSITIVE
    );

    // Mask Groq API keys (e.g. gsk_...)
    private static final Pattern GROQ_API_KEY_PATTERN = Pattern.compile(
        "gsk_[a-zA-Z0-9]{20,}"
    );

    // Mask Google Gemini API keys (e.g. AIza...)
    private static final Pattern GEMINI_API_KEY_PATTERN = Pattern.compile(
        "AIza[0-9A-Za-z-_]{35}"
    );

    // Mask sensitive parameter assignments (password=..., secret=..., etc.)
    private static final Pattern SENSITIVE_PARAM_PATTERN = Pattern.compile(
        "((?:password|secret|client_secret|apiKey|api_key|access_token|refresh_token)\\s*[=:]\\s*[\"']?)[^\"'\\s,;]+",
        Pattern.CASE_INSENSITIVE
    );

    static {
        registerFromEnv("DATABASE_URL");
        registerFromEnv("DB_USER");
        registerFromEnv("DB_PASSWORD");
        registerFromEnv("spring.datasource.url");
        registerFromEnv("spring.datasource.username");
        registerFromEnv("spring.datasource.password");
        registerFromEnv("PAYPAL_CLIENT_SECRET");
        registerFromEnv("GROQ_API_KEY");
        registerFromEnv("GEMINI_API_KEY");
        registerFromEnv("OPERATOR_PASSWORD");
        registerFromEnv("VIEWER_PASSWORD");
        registerFromEnv("DEMO_BUYER_PASSWORD");
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

        // 6. Mask bearer tokens
        masked = BEARER_TOKEN_PATTERN.matcher(masked).replaceAll("$1[MASKED_TOKEN]");

        // 7. Mask provider API keys
        masked = GROQ_API_KEY_PATTERN.matcher(masked).replaceAll("[MASKED_API_KEY]");
        masked = GEMINI_API_KEY_PATTERN.matcher(masked).replaceAll("[MASKED_API_KEY]");

        // 8. Mask sensitive parameter assignments
        masked = SENSITIVE_PARAM_PATTERN.matcher(masked).replaceAll("$1[MASKED]");

        return masked;
    }
}

