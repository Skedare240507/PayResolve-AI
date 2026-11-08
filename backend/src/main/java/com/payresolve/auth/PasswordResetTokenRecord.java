package com.payresolve.auth;

import java.time.Instant;

/**
 * Domain entity mapping to table `password_reset_tokens`.
 */
public record PasswordResetTokenRecord(
    Long id,
    Long userId,
    String tokenHash,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt
) {}

