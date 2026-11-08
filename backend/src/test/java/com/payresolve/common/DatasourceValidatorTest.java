package com.payresolve.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasourceValidatorTest {

    private static final String EXPECTED_ERROR =
        "DATABASE_URL must look like jdbc:postgresql://HOST:5432/postgres?sslmode=require, with DB_USER and DB_PASSWORD set separately.";

    @Test
    @DisplayName("Should fail when DATABASE_URL is null")
    void shouldFailWhenNull() {
        assertThatThrownBy(() -> DatasourceValidator.validate(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(EXPECTED_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres",
        "jdbc://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres",
        "http://localhost:5432/postgres",
        "jdbc:mysql://localhost:3306/db",
        "postgres://user:pass@host:5432/db"
    })
    @DisplayName("Should fail when DATABASE_URL does not start with jdbc:postgresql://")
    void shouldFailWhenDoesNotStartWithJdbcPostgresql(String invalidUrl) {
        assertThatThrownBy(() -> DatasourceValidator.validate(invalidUrl))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(EXPECTED_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:postgresql://postgres.user:secret@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres",
        "jdbc:postgresql://user:pass@localhost:5432/payresolve",
        "jdbc:postgresql://postgres@aws-0.pooler.supabase.com:5432/postgres?sslmode=require",
        "jdbc:postgresql://localhost:5432/db?user=foo@bar"
    })
    @DisplayName("Should fail when DATABASE_URL contains '@'")
    void shouldFailWhenContainsAtSymbol(String urlWithAt) {
        assertThatThrownBy(() -> DatasourceValidator.validate(urlWithAt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(EXPECTED_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:postgresql://localhost:5432/payresolve",
        "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require",
        "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require&prepareThreshold=0",
        "jdbc:postgresql://127.0.0.1:5432/payresolve"
    })
    @DisplayName("Should pass when DATABASE_URL starts with jdbc:postgresql:// and has no '@'")
    void shouldPassWhenValid(String validUrl) {
        assertThatCode(() -> DatasourceValidator.validate(validUrl))
            .doesNotThrowAnyException();
    }
}

