package com.payresolve.common;

/**
 * Validates that DATABASE_URL conforms to the required PostgreSQL JDBC format:
 * - Must start with "jdbc:postgresql://"
 * - Must NOT contain "@" (credentials must be supplied separately via DB_USER and DB_PASSWORD)
 *
 * Fails fast with an explicit, actionable error message.
 */
public final class DatasourceValidator {

    public static final String ERROR_MESSAGE =
        "DATABASE_URL must look like jdbc:postgresql://HOST:5432/postgres?sslmode=require, with DB_USER and DB_PASSWORD set separately.";

    private DatasourceValidator() {}

    /**
     * Validates that the provided DATABASE_URL is safe and correctly formatted.
     *
     * @param databaseUrl the database URL to validate
     * @throws IllegalStateException with {@link #ERROR_MESSAGE} if the URL is null,
     *                               does not start with "jdbc:postgresql://", or contains "@"
     */
    public static void validate(String databaseUrl) {
        if (databaseUrl == null || !databaseUrl.startsWith("jdbc:postgresql://") || databaseUrl.contains("@")) {
            throw new IllegalStateException(ERROR_MESSAGE);
        }
    }
}

