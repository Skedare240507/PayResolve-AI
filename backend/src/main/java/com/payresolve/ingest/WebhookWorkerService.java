package com.payresolve.ingest;

import com.payresolve.common.Money;
import com.payresolve.shop.DemoFaultSwitches;
import com.payresolve.shop.ShopOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Background worker that claims PENDING webhook records using FOR UPDATE SKIP LOCKED,
 * applies business effects idempotently, records attempts/last_error, and retries with backoff.
 */
@Service
public class WebhookWorkerService {

    private static final Logger log = LoggerFactory.getLogger(WebhookWorkerService.class);

    private final WebhookEventRepository repository;
    private final ShopOrderService shopOrderService;
    private final DemoFaultSwitches faultSwitches;
    private final com.payresolve.cases.CaseDetectionService caseDetectionService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    private final int maxAttempts;
    private final boolean workerEnabled;
    private final long initialBackoffMs;

    public WebhookWorkerService(
        WebhookEventRepository repository,
        ShopOrderService shopOrderService,
        DemoFaultSwitches faultSwitches,
        com.payresolve.cases.CaseDetectionService caseDetectionService,
        Clock clock,
        ObjectMapper objectMapper,
        @Value("${payresolve.ingest.max-attempts:3}") int maxAttempts,
        @Value("${payresolve.ingest.worker-enabled:true}") boolean workerEnabled,
        @Value("${payresolve.ingest.initial-backoff-ms:1000}") long initialBackoffMs
    ) {
        this.repository = repository;
        this.shopOrderService = shopOrderService;
        this.faultSwitches = faultSwitches;
        this.caseDetectionService = caseDetectionService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.workerEnabled = workerEnabled;
        this.initialBackoffMs = initialBackoffMs;
    }

    /**
     * Periodic scheduled worker run.
     */
    @Scheduled(fixedDelayString = "${payresolve.ingest.worker-interval-ms:2000}")
    public void runWorker() {
        if (!workerEnabled) {
            return;
        }
        processBatch(10);
    }

    /**
     * Claims and processes a batch of pending verified webhook events.
     *
     * @param batchSize maximum number of events to claim
     * @return count of successfully processed events
     */
    @Transactional
    public int processBatch(int batchSize) {
        List<WebhookEvent> pendingEvents = repository.claimPendingEvents(batchSize);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Claimed {} pending webhook events for processing", pendingEvents.size());
        int processedCount = 0;

        for (WebhookEvent event : pendingEvents) {
            if (shouldBackoff(event)) {
                log.debug("Skipping webhook event {} due to exponential backoff", event.paypalEventId());
                continue;
            }
            boolean success = processEventInternal(event);
            if (success) {
                processedCount++;
            }
        }

        return processedCount;
    }

    /**
     * Processes a single event by ID synchronously (for manual replay or deterministic tests).
     */
    @Transactional
    public boolean processSingleEvent(Long eventId) {
        return repository.findById(eventId)
            .map(this::processEventInternal)
            .orElse(false);
    }

    private boolean processEventInternal(WebhookEvent event) {
        log.info("Processing webhook event: id={}, paypalEventId={}, type={}",
            event.id(), event.paypalEventId(), event.eventType());

        // Demo Fault Switch: Fail webhook handler
        if (faultSwitches.isFailWebhookHandler()) {
            log.warn("Demo fault switch ACTIVE: Simulating handler failure for event {}", event.paypalEventId());
            handleFailure(event, "Simulated handler failure (demo fault switch active)");
            return false;
        }

        try {
            if ("PAYMENT.CAPTURE.COMPLETED".equalsIgnoreCase(event.eventType())) {
                processPaymentCaptureCompleted(event);
            } else {
                log.info("Acknowledged non-capture webhook event (type={})", event.eventType());
            }

            repository.markProcessed(event.id(), clock.instant());
            log.info("Successfully marked webhook event {} as PROCESSED", event.paypalEventId());
            caseDetectionService.detectForEvent(event);
            return true;
        } catch (Exception e) {
            log.error("Error processing webhook event {}: {}", event.paypalEventId(), e.getMessage(), e);
            handleFailure(event, e.getMessage());
            return false;
        }
    }

    private void processPaymentCaptureCompleted(WebhookEvent event) {
        try {
            JsonNode root = objectMapper.readTree(event.rawBody());
            JsonNode resource = root.get("resource");
            if (resource == null) {
                log.warn("Webhook event {} contains no resource block", event.paypalEventId());
                return;
            }

            String captureId = resource.hasNonNull("id") ? resource.get("id").asText() : event.resourceId();
            String customId = resource.hasNonNull("custom_id") ? resource.get("custom_id").asText() : null;

            Money amount = Money.of(0L, "USD");
            if (resource.hasNonNull("amount")) {
                JsonNode amountNode = resource.get("amount");
                String currency = amountNode.hasNonNull("currency_code") ? amountNode.get("currency_code").asText() : "USD";
                String value = amountNode.hasNonNull("value") ? amountNode.get("value").asText() : "0.00";
                amount = Money.parse(value, currency);
            }

            log.info("Applying payment capture effect: captureId={}, customId={}, amount={}",
                captureId, customId, amount);

            shopOrderService.applyPaymentCapture(customId, captureId, amount);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply payment capture business effect: " + e.getMessage(), e);
        }
    }

    private boolean shouldBackoff(WebhookEvent event) {
        if (event.attempts() <= 0 || event.processedAt() == null) {
            return false;
        }
        long backoffDelayMs = (long) Math.pow(2, event.attempts() - 1) * initialBackoffMs;
        Instant eligibleAt = event.processedAt().plusMillis(backoffDelayMs);
        return clock.instant().isBefore(eligibleAt);
    }

    private void handleFailure(WebhookEvent event, String errorMessage) {
        int nextAttempts = event.attempts() + 1;
        boolean willRetry = nextAttempts < maxAttempts;
        repository.recordAttemptFailure(event.id(), errorMessage, willRetry, clock.instant());

        if (willRetry) {
            log.warn("Webhook event {} attempt {}/{} failed. Event left as PENDING for retry.",
                event.paypalEventId(), nextAttempts, maxAttempts);
        } else {
            log.error("Webhook event {} exceeded maximum attempts ({}/{}). Marked as FAILED.",
                event.paypalEventId(), nextAttempts, maxAttempts);
        }
    }
}
