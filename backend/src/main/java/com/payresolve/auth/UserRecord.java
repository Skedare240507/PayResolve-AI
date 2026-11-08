package com.payresolve.auth;

import java.time.Instant;

/**
 * Domain entity mapping to table `users`.
 */
public record UserRecord(
    Long id,
    String username,
    String email,
    String passwordHash,
    String role,
    Instant createdAt,
    Instant updatedAt
) {}

