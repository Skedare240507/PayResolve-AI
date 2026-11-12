package com.payresolve.auth;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks consecutive login failures per username and locks the account for 15 minutes
 * after 5 consecutive failures. Uses the injected {@link Clock}.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_CONSECUTIVE_FAILURES = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private record AttemptState(int consecutiveFailures, Instant lockedUntil) {}

    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isLocked(String identifier) {
        String key = normalize(identifier);
        if (key.isEmpty()) {
            return false;
        }
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntil() == null) {
            return false;
        }
        if (clock.instant().isBefore(state.lockedUntil())) {
            return true;
        }
        attempts.remove(key, state);
        return false;
    }

    public void recordFailure(String identifier) {
        String key = normalize(identifier);
        if (key.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        attempts.compute(key, (ignored, current) -> {
            if (current != null && current.lockedUntil() != null) {
                if (now.isBefore(current.lockedUntil())) {
                    return current;
                }
                // Previous lockout expired; start fresh from this failure
                return new AttemptState(1, null);
            }
            int nextCount = (current == null ? 0 : current.consecutiveFailures()) + 1;
            Instant lockUntil = nextCount >= MAX_CONSECUTIVE_FAILURES ? now.plus(LOCKOUT_DURATION) : null;
            return new AttemptState(nextCount, lockUntil);
        });
    }

    public void clearFailures(String identifier) {
        String key = normalize(identifier);
        if (!key.isEmpty()) {
            attempts.remove(key);
        }
    }

    public void resetAll() {
        attempts.clear();
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }
}

