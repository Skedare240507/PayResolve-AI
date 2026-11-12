package com.payresolve.ingest;

import com.payresolve.common.RateLimitService;
import com.payresolve.paypal.PayPalWebhookVerificationClient;
import com.payresolve.shop.DemoFaultSwitches;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Service for ingesting PayPal webhooks with signature verification,
 * quarantine of unverified attempts, poisoning-resistant deduplication, and demo fault switches.
 */
@Service
public class WebhookIngestService {

    public static final class UnverifiedWebhookRateLimitException extends RuntimeException {
        private final long retryAfterSeconds;

        public UnverifiedWebhookRateLimitException(long retryAfterSeconds) {
            super("Too many unverified webhook attempts from this IP.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestService.class);
    private static final ThreadLocal<Boolean> DUPLICATING = ThreadLocal.withInitial(() -> false);
    private static final int UNVERIFIED_MAX_PER_HOUR = 20;

    private final WebhookEventRepository repository;
    private final RejectedWebhookRepository rejectedWebhookRepository;
    private final PayPalWebhookVerificationClient verificationClient;
    private final DemoFaultSwitches faultSwitches;
    private final RateLimitService rateLimitService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookIngestService(
        WebhookEventRepository repository,
        RejectedWebhookRepository rejectedWebhookRepository,
        PayPalWebhookVerificationClient verificationClient,
        DemoFaultSwitches faultSwitches,
        RateLimitService rateLimitService,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.rejectedWebhookRepository = rejectedWebhookRepository;
        this.verificationClient = verificationClient;
        this.faultSwitches = faultSwitches;
        this.rateLimitService = rateLimitService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public IngestResult ingest(String rawBody, Map<String, String> headers) {
        return ingest(rawBody, headers, "unknown");
    }

    /**
     * Ingests an incoming raw webhook notification payload.
     * - Verified events are persisted/upgraded in webhook_events and return 200 fast.
     * - Duplicate verified deliveries increment delivery_count and publish a domain event for audit case recording.
     * - Unverified attempts are rate limited per IP and stored in rejected_webhooks (never modifying webhook_events).
     */
    @Transactional
    public IngestResult ingest(String rawBody, Map<String, String> headers, String clientIp) {
        Objects.requireNonNull(rawBody, "Webhook rawBody must not be null");

        String paypalEventId = null;
        String eventType = "UNKNOWN";
        String resourceId = null;
        Instant eventCreatedAt = null;
        String customId = null;
        String paypalOrderId = null;
        Map<String, Object> eventMap = null;
        boolean parseError = false;

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root.hasNonNull("id")) {
                paypalEventId = root.get("id").asText();
            }
            if (root.hasNonNull("event_type")) {
                eventType = root.get("event_type").asText();
            }
            if (root.hasNonNull("create_time")) {
                try {
                    eventCreatedAt = Instant.parse(root.get("create_time").asText());
                } catch (Exception ignored) {
                }
            }
            if (root.hasNonNull("resource")) {
                JsonNode res = root.get("resource");
                if (res.hasNonNull("id")) {
                    resourceId = res.get("id").asText();
                }
                if (res.hasNonNull("custom_id")) {
                    customId = res.get("custom_id").asText();
                } else if (res.hasNonNull("purchase_units") && res.get("purchase_units").isArray() && res.get("purchase_units").size() > 0) {
                    JsonNode pu = res.get("purchase_units").get(0);
                    if (pu.hasNonNull("custom_id")) {
                        customId = pu.get("custom_id").asText();
                    }
                }

                if (res.hasNonNull("supplementary_data")) {
                    JsonNode supp = res.get("supplementary_data");
                    if (supp.hasNonNull("related_ids") && supp.get("related_ids").hasNonNull("order_id")) {
                        paypalOrderId = supp.get("related_ids").get("order_id").asText();
                    }
                }
                if (paypalOrderId == null && res.hasNonNull("order_id")) {
                    paypalOrderId = res.get("order_id").asText();
                }
                if (paypalOrderId == null && eventType != null && eventType.startsWith("CHECKOUT.ORDER")) {
                    paypalOrderId = resourceId;
                }
            }
            eventMap = objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse incoming webhook JSON: {}", e.getMessage());
            parseError = true;
            paypalEventId = "INVALID-" + clock.millis();
        }

        // Demo Fault Switch 1: Drop webhook
        if (faultSwitches.isDropWebhook()) {
            log.warn("Demo fault switch ACTIVE: Dropping incoming PayPal webhook (paypalEventId={})", paypalEventId);
            return IngestResult.dropped(paypalEventId);
        }

        // Verify webhook signature with PayPal
        boolean verified = false;
        if (!parseError && eventMap != null && headers != null) {
            try {
                verified = verificationClient.verifySignature(headers, eventMap);
            } catch (Exception e) {
                log.error("Error invoking webhook signature verification: {}", e.getMessage());
            }
        }

        if (!verified) {
            String safeIp = clientIp != null && !clientIp.isBlank() ? clientIp : "unknown";
            var decision = rateLimitService.checkAndConsume(
                "unverified-webhook:ip:" + safeIp,
                UNVERIFIED_MAX_PER_HOUR,
                Duration.ofHours(1)
            );
            if (!decision.allowed()) {
                throw new UnverifiedWebhookRateLimitException(decision.retryAfterSeconds());
            }

            String reason = parseError ? "Malformed JSON payload" : "PayPal signature verification failed";
            rejectedWebhookRepository.insert(
                clock.instant(),
                paypalEventId,
                reason,
                sha256Hex(rawBody),
                safeIp
            );
            log.warn("Quarantined UNVERIFIED webhook (paypalEventId={}, reason={}, ip={}) in rejected_webhooks",
                paypalEventId, reason, safeIp);
            return new IngestResult(0L, paypalEventId, eventType, false, "UNVERIFIED", false, false);
        }

        // Serialize incoming headers for diagnostic audit
        String headersJson = null;
        try {
            if (headers != null) {
                headersJson = objectMapper.writeValueAsString(headers);
            }
        } catch (Exception e) {
            log.warn("Could not serialize headers to JSON: {}", e.getMessage());
        }

        // Upsert verified event: upgrades unverified rows in place, increments delivery_count on verified duplicates
        WebhookEventRepository.WebhookUpsertResult upsert = repository.upsert(
            paypalEventId,
            eventType,
            resourceId,
            rawBody,
            headersJson,
            true,
            "PENDING",
            eventCreatedAt,
            customId,
            paypalOrderId
        );

        Long id = upsert.id();
        int deliveryCount = upsert.deliveryCount();

        if (deliveryCount > 1) {
            log.info("Duplicate verified PayPal webhook event received (paypalEventId={}, deliveryCount={}, mismatch={}). Incremented count and returning 200.",
                paypalEventId, deliveryCount, upsert.payloadMismatch());
            eventPublisher.publishEvent(new DuplicateWebhookDeliveredEvent(paypalEventId));
        } else {
            log.info("Enqueued verified webhook event (id={}, paypalEventId={}, type={}) for asynchronous processing",
                id, paypalEventId, eventType);
        }

        // Demo Fault Switch 2: Duplicate delivery simulation
        if (faultSwitches.isDuplicateDelivery() && !DUPLICATING.get()) {
            log.info("Demo fault switch ACTIVE: Simulating duplicate delivery for event {}", paypalEventId);
            try {
                DUPLICATING.set(true);
                ingest(rawBody, headers, clientIp);
            } finally {
                DUPLICATING.set(false);
            }
        }

        if (deliveryCount > 1) {
            return IngestResult.duplicate(id, paypalEventId, eventType, true, "PENDING");
        }
        return IngestResult.accepted(id, paypalEventId, eventType, true, "PENDING");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
