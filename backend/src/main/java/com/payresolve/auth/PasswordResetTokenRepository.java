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
 * JDBC data access for password reset tokens in `password_reset_tokens`.
 */
@Repository
public class PasswordResetTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenRepository.class);

    private final JdbcClient jdbcClient;
    private final TokenRowMapper rowMapper = new TokenRowMapper();

    public PasswordResetTokenRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(PasswordResetTokenRecord record) {
        String sql = """
            INSERT INTO password_reset_tokens (
                user_id, token_hash, expires_at, used_at, created_at
            ) VALUES (
                :userId, :tokenHash, :expiresAt, :usedAt, :createdAt
            )
            """;

        Instant createdAt = record.createdAt() != null ? record.createdAt() : Instant.now();
        Timestamp usedTs = record.usedAt() != null ? Timestamp.from(record.usedAt()) : null;

        jdbcClient.sql(sql)
            .param("userId", record.userId())
            .param("tokenHash", record.tokenHash())
            .param("expiresAt", Timestamp.from(record.expiresAt()))
            .param("usedAt", usedTs)
            .param("createdAt", Timestamp.from(createdAt))
            .update();

        log.debug("Created password reset token for userId={}", record.userId());
    }

    public Optional<PasswordResetTokenRecord> findByToken(String tokenHash) {
        String sql = """
            SELECT id, user_id, token_hash, expires_at, used_at, created_at
            FROM password_reset_tokens
            WHERE token_hash = :tokenHash
            """;
        return jdbcClient.sql(sql)
            .param("tokenHash", tokenHash)
            .query(rowMapper)
            .optional();
    }

    public void markUsed(Long id, Instant usedAt) {
        String sql = "UPDATE password_reset_tokens SET used_at = :usedAt WHERE id = :id";
        jdbcClient.sql(sql)
            .param("id", id)
            .param("usedAt", Timestamp.from(usedAt))
            .update();
        log.debug("Marked reset token id={} as used", id);
    }

    private static class TokenRowMapper implements RowMapper<PasswordResetTokenRecord> {
        @Override
        public PasswordResetTokenRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp expiresTs = rs.getTimestamp("expires_at");
            Timestamp usedTs = rs.getTimestamp("used_at");
            Timestamp createdTs = rs.getTimestamp("created_at");

            return new PasswordResetTokenRecord(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("token_hash"),
                expiresTs != null ? expiresTs.toInstant() : null,
                usedTs != null ? usedTs.toInstant() : null,
                createdTs != null ? createdTs.toInstant() : null
            );
        }
    }
}

