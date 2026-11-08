package com.payresolve.ingest;

import com.payresolve.paypal.PayPalWebhookVerificationClient;
import com.payresolve.shop.DemoFaultSwitches;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Service for ingesting PayPal webhooks with signature verification,
 * deduplication, and demo fault switches.
 */
@Service
public class WebhookIngestService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestService.class);
    private static final ThreadLocal<Boolean> DUPLICATING = ThreadLocal.withInitial(() -> false);

    private final WebhookEventRepository repository;
    private final PayPalWebhookVerificationClient verificationClient;
    private final DemoFaultSwitches faultSwitches;
    private final ObjectMapper objectMapper;

    public WebhookIngestService(
        WebhookEventRepository repository,
        PayPalWebhookVerificationClient verificationClient,
        DemoFaultSwitches faultSwitches,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.verificationClient = verificationClient;
        this.faultSwitches = faultSwitches;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingests an incoming raw webhook notification payload.
     * Fast path: persists event to durable inbox and returns 200.
     * Duplicate event IDs increment delivery_count and update last_received_at, returning HTTP 200.
     * Unverified events are stored as verified=false and never processed.
     */
    @Transactional
    public IngestResult ingest(String rawBody, Map<String, String> headers) {
        Objects.requireNonNull(rawBody, "Webhook rawBody must not be null");

        String paypalEventId = null;
        String eventType = "UNKNOWN";
        String resourceId = null;
        Instant eventCreatedAt = null;
        String customId = null;
        String paypalOrderId = null;
        Map<String, Object> eventMap = null;

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
            paypalEventId = "INVALID-" + System.currentTimeMillis();
        }

        // Demo Fault Switch 1: Drop webhook
        if (faultSwitches.isDropWebhook()) {
            log.warn("Demo fault switch ACTIVE: Dropping incoming PayPal webhook (paypalEventId={})", paypalEventId);
            return IngestResult.dropped(paypalEventId);
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

        // Verify webhook signature with PayPal
        boolean verified = false;
        if (eventMap != null && headers != null) {
            try {
                verified = verificationClient.verifySignature(headers, eventMap);
            } catch (Exception e) {
                log.error("Error invoking webhook signature verification: {}", e.getMessage());
            }
        }

        String status = verified ? "PENDING" : "UNVERIFIED";

        // Upsert event: increments delivery_count and sets last_received_at on conflict
        WebhookEventRepository.WebhookUpsertResult upsert = repository.upsert(
            paypalEventId,
            eventType,
            resourceId,
            rawBody,
            headersJson,
            verified,
            status,
            eventCreatedAt,
            customId,
            paypalOrderId
        );

        Long id = upsert.id();
        int deliveryCount = upsert.deliveryCount();

        if (deliveryCount > 1) {
            log.info("Duplicate PayPal webhook event received (paypalEventId={}, deliveryCount={}). Incremented count and returning 200.",
                paypalEventId, deliveryCount);
        } else if (!verified) {
            log.warn("Stored UNVERIFIED webhook event (id={}, paypalEventId={}). Will never be processed.",
                id, paypalEventId);
            repository.recordAttemptFailure(id, "PayPal signature verification failed", false);
        } else {
            log.info("Enqueued verified webhook event (id={}, paypalEventId={}, type={}) for asynchronous processing",
                id, paypalEventId, eventType);
        }

        // Demo Fault Switch 2: Duplicate delivery simulation
        if (faultSwitches.isDuplicateDelivery() && !DUPLICATING.get()) {
            log.info("Demo fault switch ACTIVE: Simulating duplicate delivery for event {}", paypalEventId);
            try {
                DUPLICATING.set(true);
                ingest(rawBody, headers);
            } finally {
                DUPLICATING.set(false);
            }
        }

        if (deliveryCount > 1) {
            return IngestResult.duplicate(id, paypalEventId, eventType, verified, status);
        }
        return IngestResult.accepted(id, paypalEventId, eventType, verified, status);
    }
}
