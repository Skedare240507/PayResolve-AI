package com.payresolve.paypal;

import java.time.Instant;
import java.util.Optional;

/**
 * Service interface allowing the PayPal diagnostics module to query the timestamp of
 * the most recently received webhook event without depending on the ingest module directly.
 */
@FunctionalInterface
public interface LastWebhookTimeProvider {
    Optional<Instant> findLastWebhookReceivedAt();
}

