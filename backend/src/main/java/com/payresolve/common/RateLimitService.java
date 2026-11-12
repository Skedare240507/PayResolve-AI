package com.payresolve.common;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter keyed by bucket identifier (IP or username).
 * Uses the injected {@link Clock} so expiration and Retry-After windows are deterministic and testable.
 */
@Service
public class RateLimitService {

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {}

    private static final int MAX_TRACKED_BUCKETS = 20_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Checks whether the bucket may perform an action and consumes one slot if allowed.
     */
    public RateLimitDecision checkAndConsume(String bucketKey, int maxRequests, Duration window) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);

        if (buckets.size() > MAX_TRACKED_BUCKETS) {
            pruneStaleBuckets(cutoff);
        }

        Deque<Instant> timestamps = buckets.computeIfAbsent(bucketKey, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                Instant oldest = timestamps.peekFirst();
                long millisRemaining = oldest != null
                    ? Math.max(1_000L, Duration.between(now, oldest.plus(window)).toMillis())
                    : window.toMillis();
                long retryAfterSeconds = Math.max(1L, (millisRemaining + 999L) / 1000L);
                return new RateLimitDecision(false, retryAfterSeconds);
            }

            timestamps.addLast(now);
            return new RateLimitDecision(true, 0L);
        }
    }

    /**
     * Resets all in-memory buckets (intended for integration tests).
     */
    public void resetAll() {
        buckets.clear();
    }

    private void pruneStaleBuckets(Instant cutoff) {
        buckets.entrySet().removeIf(entry -> {
            Deque<Instant> dq = entry.getValue();
            synchronized (dq) {
                while (!dq.isEmpty() && !dq.peekFirst().isAfter(cutoff)) {
                    dq.pollFirst();
                }
                return dq.isEmpty();
            }
        });
    }
}

