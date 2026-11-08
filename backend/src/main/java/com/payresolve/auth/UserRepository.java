package com.payresolve.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC data access for table `users`.
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final JdbcClient jdbcClient;
    private final UserRowMapper rowMapper = new UserRowMapper();

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UserRecord> findByUsername(String username) {
        String sql = """
            SELECT id, username, email, password_hash, role, created_at, created_at AS updated_at
            FROM users
            WHERE LOWER(username) = LOWER(:username)
            """;
        return jdbcClient.sql(sql)
            .param("username", username.trim())
            .query(rowMapper)
            .optional();
    }

    public Optional<UserRecord> findByEmail(String email) {
        String sql = """
            SELECT id, username, email, password_hash, role, created_at, created_at AS updated_at
            FROM users
            WHERE LOWER(email) = LOWER(:email)
            """;
        return jdbcClient.sql(sql)
            .param("email", email.trim())
            .query(rowMapper)
            .optional();
    }

    public Optional<UserRecord> findById(Long id) {
        String sql = """
            SELECT id, username, email, password_hash, role, created_at, created_at AS updated_at
            FROM users
            WHERE id = :id
            """;
        return jdbcClient.sql(sql)
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public Long insert(UserRecord record) {
        String sql = """
            INSERT INTO users (
                username, email, password_hash, role, created_at
            ) VALUES (
                :username, :email, :passwordHash, :role, :createdAt
            )
            """;

        Instant now = record.createdAt() != null ? record.createdAt() : Instant.now();

        jdbcClient.sql(sql)
            .param("username", record.username().trim())
            .param("email", record.email().trim())
            .param("passwordHash", record.passwordHash())
            .param("role", record.role().toUpperCase())
            .param("createdAt", Timestamp.from(now))
            .update();

        return findByUsername(record.username())
            .map(UserRecord::id)
            .orElseThrow(() -> new IllegalStateException("Failed to retrieve created user: " + record.username()));
    }

    public void updatePassword(Long id, String passwordHash) {
        String sql = "UPDATE users SET password_hash = :passwordHash WHERE id = :id";
        jdbcClient.sql(sql)
            .param("id", id)
            .param("passwordHash", passwordHash)
            .update();
        log.info("Updated password for user id={}", id);
    }

    private static class UserRowMapper implements RowMapper<UserRecord> {
        @Override
        public UserRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdTs = rs.getTimestamp("created_at");
            Timestamp updatedTs = rs.getTimestamp("updated_at");

            return new UserRecord(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("role"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null
            );
        }
    }
}

