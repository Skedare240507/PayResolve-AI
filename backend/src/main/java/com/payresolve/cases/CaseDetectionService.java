package com.payresolve.cases;

import com.payresolve.cases.dto.DetectionRunResultDto;
import com.payresolve.common.Money;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.ingest.WebhookEventRepository;
import com.payresolve.paypal.model.Amount;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShopOrder;
import com.payresolve.shop.ShopOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service that builds snapshots from system state, invokes pure Detector instances,
 * and records findings as exception cases.
 */
@Service
public class CaseDetectionService {

    private static final Logger log = LoggerFactory.getLogger(CaseDetectionService.class);

    private final List<Detector> detectors;
    private final ShopOrderService shopOrderService;
    private final WebhookEventRepository webhookEventRepository;
    private final CaseService caseService;
    private final Clock clock;

    public CaseDetectionService(
        List<Detector> detectors,
        ShopOrderService shopOrderService,
        WebhookEventRepository webhookEventRepository,
        CaseService caseService,
        Clock clock
    ) {
        this.detectors = List.copyOf(detectors);
        this.shopOrderService = shopOrderService;
        this.webhookEventRepository = webhookEventRepository;
        this.caseService = caseService;
        this.clock = clock;
    }

    /**
     * Executes all detectors across the entire state of shop orders, payments, and events.
     * Enforces that reruns update existing cases rather than creating duplicates.
     *
     * @return summary metrics of the detection run
     */
    @Transactional
    public DetectionRunResultDto runDetection() {
        log.info("Starting on-demand payment discrepancy detection run with {} detectors", detectors.size());
        List<ShopOrder> orders = shopOrderService.findAllOrders(1000, 0);
        List<WebhookEvent> allEvents = webhookEventRepository.findAll(500, 0);

        int totalEvaluated = 0;
        int openedCount = 0;
        int updatedCount = 0;

        // 1. Evaluate each known merchant shop order
        for (ShopOrder order : orders) {
            totalEvaluated++;
            DetectionSnapshot snapshot = buildSnapshotForOrder(order, allEvents);
            for (Detector detector : detectors) {
                List<DetectionFinding> findings = detector.detect(snapshot);
                for (DetectionFinding finding : findings) {
                    boolean existed = caseService.getCases(null, null, finding.type().name(), null, 0, 100)
                        .content().stream()
                        .anyMatch(c -> Objects.equals(c.type(), finding.type().name())
                                    && (Objects.equals(c.order(), "#" + order.id()) || Objects.equals(c.order(), order.id())));

                    caseService.upsertCase(finding);
                    if (existed) {
                        updatedCount++;
                    } else {
                        openedCount++;
                    }
                }
            }
        }

        // 2. Evaluate orphan captures from webhook events that have no associated shop order
        Set<String> knownOrderIds = new HashSet<>();
        orders.forEach(o -> {
            knownOrderIds.add(o.id());
            if (o.paypalOrderId() != null) {
                knownOrderIds.add(o.paypalOrderId());
            }
        });

        for (WebhookEvent event : allEvents) {
            if ("PAYMENT.CAPTURE.COMPLETED".equalsIgnoreCase(event.eventType()) && event.verified()) {
                boolean matchesAnyOrder = knownOrderIds.stream()
                    .anyMatch(id -> event.rawBody() != null && event.rawBody().contains(id));

                if (!matchesAnyOrder) {
                    totalEvaluated++;
                    DetectionSnapshot orphanSnapshot = DetectionSnapshot.builder()
                        .targetId(event.resourceId() != null ? event.resourceId() : "CAP-" + event.paypalEventId())
                        .shopOrder(null)
                        .addWebhookEvent(event)
                        .evaluatedAt(clock.instant())
                        .build();

                    for (Detector detector : detectors) {
                        if (detector.supportedType() == CaseType.ORPHAN_CAPTURE) {
                            List<DetectionFinding> findings = detector.detect(orphanSnapshot);
                            for (DetectionFinding finding : findings) {
                                caseService.upsertCase(finding);
                                openedCount++;
                            }
                        }
                    }
                }
            }
        }

        long totalCases = caseService.getCases(null, null, null, null, 0, 1).totalElements();
        log.info("Detection run finished: evaluated={}, opened={}, updated={}, totalCases={}",
            totalEvaluated, openedCount, updatedCount, totalCases);

        return new DetectionRunResultDto(
            totalEvaluated,
            openedCount,
            updatedCount,
            totalCases,
            String.format("Detection complete. Evaluated %d entities: %d discrepancies opened, %d updated.",
                totalEvaluated, openedCount, updatedCount)
        );
    }

    /**
     * Runs detectors immediately following the processing of a specific webhook event.
     *
     * @param event the processed webhook event
     */
    public void detectForEvent(WebhookEvent event) {
        if (event == null) {
            return;
        }

        try {
            log.debug("Running event-driven detection for event id={}, type={}", event.id(), event.eventType());
            List<WebhookEvent> events = List.of(event);

            // Attempt to resolve associated shop order
            ShopOrder targetOrder = null;
            List<ShopOrder> recentOrders = shopOrderService.findAllOrders(50, 0);
            for (ShopOrder order : recentOrders) {
                if (event.rawBody() != null && (event.rawBody().contains(order.id())
                    || (order.paypalOrderId() != null && event.rawBody().contains(order.paypalOrderId())))) {
                    targetOrder = order;
                    break;
                }
            }

            DetectionSnapshot snapshot = targetOrder != null
                ? buildSnapshotForOrder(targetOrder, events)
                : DetectionSnapshot.builder()
                    .targetId(event.resourceId() != null ? event.resourceId() : "EVT-" + event.paypalEventId())
                    .shopOrder(null)
                    .addWebhookEvent(event)
                    .evaluatedAt(clock.instant())
                    .build();

            for (Detector detector : detectors) {
                List<DetectionFinding> findings = detector.detect(snapshot);
                for (DetectionFinding finding : findings) {
                    caseService.upsertCase(finding);
                }
            }
        } catch (Exception e) {
            log.error("Failed to run event-driven detection for event {}: {}", event.paypalEventId(), e.getMessage(), e);
        }
    }

    private DetectionSnapshot buildSnapshotForOrder(ShopOrder order, List<WebhookEvent> allEvents) {
        List<PaymentRecord> payments = shopOrderService.findPaymentByOrderId(order.id())
            .map(List::of)
            .orElse(List.of());

        List<RefundRecord> refunds = shopOrderService.getRefundsByOrderId(order.id());

        List<WebhookEvent> relevantEvents = allEvents.stream()
            .filter(e -> (order.paypalOrderId() != null && e.rawBody() != null && e.rawBody().contains(order.paypalOrderId()))
                      || (e.rawBody() != null && e.rawBody().contains(order.id()))
                      || (payments.stream().anyMatch(p -> p.paypalCaptureId() != null && Objects.equals(p.paypalCaptureId(), e.resourceId()))))
            .toList();

        return DetectionSnapshot.builder()
            .targetId(order.paypalOrderId() != null ? order.paypalOrderId() : order.id())
            .shopOrder(order)
            .shopPayments(payments)
            .shopRefunds(refunds)
            .webhookEvents(relevantEvents)
            .evaluatedAt(clock.instant())
            .gracePeriod(Duration.ZERO)
            .build();
    }
}
