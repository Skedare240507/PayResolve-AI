package com.payresolve.cases;

import com.payresolve.cases.dto.DetectionRunResultDto;
import com.payresolve.common.Money;
import com.payresolve.ingest.DuplicateWebhookDeliveredEvent;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

/**
 * Service that builds snapshots from system state using indexed columns (custom_id, paypal_order_id,
 * resource_id), invokes pure Detector instances, and records findings as exception cases.
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

        // 2. Evaluate orphan captures from verified webhook events using structured ID columns
        Set<String> knownShopOrderIds = new HashSet<>();
        Set<String> knownPaypalOrderIds = new HashSet<>();
        orders.forEach(o -> {
            knownShopOrderIds.add(o.id());
            if (o.paypalOrderId() != null) {
                knownPaypalOrderIds.add(o.paypalOrderId());
            }
        });

        for (WebhookEvent event : allEvents) {
            if ("PAYMENT.CAPTURE.COMPLETED".equalsIgnoreCase(event.eventType()) && event.verified()) {
                boolean matchesOrder = (event.customId() != null && knownShopOrderIds.contains(event.customId()))
                    || (event.paypalOrderId() != null && knownPaypalOrderIds.contains(event.paypalOrderId()))
                    || (event.resourceId() != null && knownPaypalOrderIds.contains(event.resourceId()));

                if (!matchesOrder) {
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

    @EventListener
    public void onDuplicateWebhookDelivered(DuplicateWebhookDeliveredEvent domainEvent) {
        if (domainEvent == null || domainEvent.paypalEventId() == null) {
            return;
        }
        webhookEventRepository.findByPaypalEventId(domainEvent.paypalEventId())
            .ifPresent(this::detectForEvent);
    }

    /**
     * Runs detectors immediately following the processing or duplicate delivery of a webhook event,
     * matching strictly via indexed custom_id / paypal_order_id columns.
     */
    public void detectForEvent(WebhookEvent event) {
        if (event == null || !event.verified()) {
            return;
        }

        try {
            log.debug("Running event-driven detection for event id={}, type={}", event.id(), event.eventType());

            ShopOrder targetOrder = null;
            if (event.customId() != null && !event.customId().isBlank()) {
                targetOrder = shopOrderService.findOrderById(event.customId()).orElse(null);
            }
            if (targetOrder == null && event.paypalOrderId() != null && !event.paypalOrderId().isBlank()) {
                targetOrder = shopOrderService.findOrderByPayPalOrderId(event.paypalOrderId()).orElse(null);
            }

            List<WebhookEvent> events = targetOrder != null
                ? webhookEventRepository.findByOrderRefs(targetOrder.id(), targetOrder.paypalOrderId(), null)
                : List.of(event);
            if (events.isEmpty()) {
                events = List.of(event);
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

    private DetectionSnapshot buildSnapshotForOrder(ShopOrder order, List<WebhookEvent> candidateEvents) {
        List<PaymentRecord> payments = shopOrderService.findPaymentByOrderId(order.id())
            .map(List::of)
            .orElse(List.of());

        List<RefundRecord> refunds = shopOrderService.getRefundsByOrderId(order.id());

        List<WebhookEvent> relevantEvents = candidateEvents.stream()
            .filter(WebhookEvent::verified)
            .filter(e -> Objects.equals(order.id(), e.customId())
                      || (order.paypalOrderId() != null && Objects.equals(order.paypalOrderId(), e.paypalOrderId()))
                      || (order.paypalOrderId() != null && Objects.equals(order.paypalOrderId(), e.resourceId()))
                      || (order.paypalCaptureId() != null && Objects.equals(order.paypalCaptureId(), e.resourceId()))
                      || payments.stream().anyMatch(p -> p.paypalCaptureId() != null && Objects.equals(p.paypalCaptureId(), e.resourceId())))
            .toList();

        DetectionSnapshot.Builder builder = DetectionSnapshot.builder()
            .targetId(order.paypalOrderId() != null ? order.paypalOrderId() : order.id())
            .shopOrder(order)
            .shopPayments(payments)
            .shopRefunds(refunds)
            .webhookEvents(relevantEvents)
            .evaluatedAt(clock.instant())
            .gracePeriod(Duration.ZERO);

        if (order.isAwaitingConfirmation()
            && payments.isEmpty()
            && order.capturedAt() != null
            && clock.instant().isAfter(order.capturedAt().plusSeconds(60))) {
            String capId = order.paypalCaptureId() != null ? order.paypalCaptureId() : "CAP-" + order.id();
            builder.addPaypalCapture(new CaptureSummary(
                capId,
                "COMPLETED",
                Amount.fromMoney(order.total()),
                Boolean.TRUE,
                null,
                order.capturedAt().toString(),
                order.capturedAt().toString()
            ));
        }

        return builder.build();
    }
}
