package com.payresolve.common;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafeDataSourceBeanPostProcessorTest {

    private final SafeDataSourceBeanPostProcessor processor = new SafeDataSourceBeanPostProcessor();

    @Test
    @DisplayName("Should fail fast when HikariDataSource has URL not starting with jdbc:postgresql://")
    void shouldFailWhenInvalidScheme() {
        HikariDataSource ds = mock(HikariDataSource.class);
        when(ds.getJdbcUrl()).thenReturn("postgresql://aws-0.pooler.supabase.com:5432/postgres");

        assertThatThrownBy(() -> processor.postProcessBeforeInitialization(ds, "dataSource"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("DATABASE_URL must look like jdbc:postgresql://HOST:5432/postgres?sslmode=require, with DB_USER and DB_PASSWORD set separately.");
    }

    @Test
    @DisplayName("Should fail fast when HikariDataSource has URL containing '@'")
    void shouldFailWhenUrlContainsAtSymbol() {
        HikariDataSource ds = mock(HikariDataSource.class);
        when(ds.getJdbcUrl()).thenReturn("jdbc:postgresql://user:pass@aws-0.pooler.supabase.com:5432/postgres");

        assertThatThrownBy(() -> processor.postProcessBeforeInitialization(ds, "dataSource"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("DATABASE_URL must look like jdbc:postgresql://HOST:5432/postgres?sslmode=require, with DB_USER and DB_PASSWORD set separately.");
    }

    @Test
    @DisplayName("Should pass when HikariDataSource has valid PostgreSQL JDBC URL and register credentials")
    void shouldPassWhenValidPostgresUrl() {
        HikariDataSource ds = mock(HikariDataSource.class);
        when(ds.getJdbcUrl()).thenReturn("jdbc:postgresql://aws-0.pooler.supabase.com:5432/postgres?sslmode=require");
        when(ds.getUsername()).thenReturn("my_pg_user");
        when(ds.getPassword()).thenReturn("my_pg_pass");

        assertThatCode(() -> processor.postProcessBeforeInitialization(ds, "dataSource"))
            .doesNotThrowAnyException();

        // Verify credentials were registered for masking
        assertThat(SensitiveDataMasker.getRegisteredValues()).contains("my_pg_user");
        assertThat(SensitiveDataMasker.getRegisteredValues()).contains("my_pg_pass");
    }

    @Test
    @DisplayName("Should pass when HikariDataSource uses H2 for tests")
    void shouldPassWhenH2() {
        HikariDataSource ds = mock(HikariDataSource.class);
        when(ds.getJdbcUrl()).thenReturn("jdbc:h2:mem:testdb;MODE=PostgreSQL");

        assertThatCode(() -> processor.postProcessBeforeInitialization(ds, "dataSource"))
            .doesNotThrowAnyException();
    }
}
