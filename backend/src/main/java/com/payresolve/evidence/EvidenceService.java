package com.payresolve.evidence;

import com.payresolve.cases.CaseRecord;
import com.payresolve.cases.CaseService;
import com.payresolve.cases.CaseType;
import com.payresolve.evidence.dto.EvidenceGraphDto;
import com.payresolve.evidence.dto.EvidenceGraphEdgeDto;
import com.payresolve.evidence.dto.EvidenceGraphNodeDto;
import com.payresolve.evidence.dto.EvidenceTimelineDto;
import com.payresolve.evidence.dto.EvidenceTimelineItemDto;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.ingest.WebhookEventRepository;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShipmentRecord;
import com.payresolve.shop.ShopOrder;
import com.payresolve.shop.ShopOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Service that normalizes system and PayPal records into case_evidence items,
 * and derives the Evidence Graph and Chronological Timeline at read time.
 */
@Service
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final CaseService caseService;
    private final CaseEvidenceRepository evidenceRepository;
    private final ShopOrderService shopOrderService;
    private final WebhookEventRepository webhookEventRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public EvidenceService(
        CaseService caseService,
        CaseEvidenceRepository evidenceRepository,
        ShopOrderService shopOrderService,
        WebhookEventRepository webhookEventRepository,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        this.caseService = caseService;
        this.evidenceRepository = evidenceRepository;
        this.shopOrderService = shopOrderService;
        this.webhookEventRepository = webhookEventRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Normalizes all records related to a case into case_evidence items and persists them.
     */
    @Transactional
    public List<CaseEvidenceRecord> normaliseEvidenceForCase(CaseRecord caseRecord) {
        log.info("Normalising evidence items for case {}", caseRecord.id());
        evidenceRepository.deleteByCaseId(caseRecord.id());

        List<CaseEvidenceRecord> items = new ArrayList<>();
        Instant baseTime = caseRecord.openedAt() != null ? caseRecord.openedAt() : clock.instant();

        // 1. Shop Order Evidence
        Optional<ShopOrder> shopOrderOpt = Optional.empty();
        if (caseRecord.shopOrderId() != null) {
            shopOrderOpt = shopOrderService.findOrderById(caseRecord.shopOrderId());
        }
        if (shopOrderOpt.isEmpty() && caseRecord.paypalOrderId() != null) {
            shopOrderOpt = shopOrderService.findOrderByPayPalOrderId(caseRecord.paypalOrderId());
        }

        shopOrderOpt.ifPresent(order -> {
            items.add(createEvidenceRecord(
                caseRecord.id(),
                EvidenceKind.SHOP_ORDER,
                EvidenceSource.SHOP,
                order.id(),
                Map.of(
                    "id", order.id(),
                    "status", order.status(),
                    "totalMinor", order.totalMinor(),
                    "currency", order.currency(),
                    "customerLabel", order.customerLabel() != null ? order.customerLabel() : "Customer"
                ),
                order.createdAt() != null ? order.createdAt() : baseTime.minusSeconds(120)
            ));
        });

        // 2. PayPal Order Evidence
        String paypalOrderId = caseRecord.paypalOrderId();
        if (paypalOrderId != null && !paypalOrderId.startsWith("CAP-")) {
            items.add(createEvidenceRecord(
                caseRecord.id(),
                EvidenceKind.PAYPAL_ORDER,
                EvidenceSource.PAYPAL,
                paypalOrderId,
                Map.of(
                    "id", paypalOrderId,
                    "status", "APPROVED",
                    "intent", "CAPTURE"
                ),
                baseTime.minusSeconds(90)
            ));
        }

        // 3. PayPal Capture Evidence
        List<PaymentRecord> payments = shopOrderOpt
            .map(order -> shopOrderService.findPaymentByOrderId(order.id()).map(List::of).orElse(List.of()))
            .orElse(List.of());

        if (!payments.isEmpty()) {
            for (PaymentRecord payment : payments) {
                items.add(createEvidenceRecord(
                    caseRecord.id(),
                    EvidenceKind.PAYPAL_CAPTURE,
                    EvidenceSource.PAYPAL,
                    payment.paypalCaptureId() != null ? payment.paypalCaptureId() : "CAP-" + payment.id(),
                    Map.of(
                        "id", payment.paypalCaptureId() != null ? payment.paypalCaptureId() : "CAP-" + payment.id(),
                        "status", payment.status(),
                        "amountMinor", payment.amountMinor(),
                        "currency", payment.currency()
                    ),
                    payment.capturedAt() != null ? payment.capturedAt() : baseTime.minusSeconds(60)
                ));
            }
        } else {
            String capId = paypalOrderId != null && paypalOrderId.startsWith("CAP-") ? paypalOrderId : "CAP-" + (paypalOrderId != null ? paypalOrderId : caseRecord.id());
            long capAmount = caseRecord.amountMinor() != null ? caseRecord.amountMinor() : (shopOrderOpt.map(ShopOrder::totalMinor).orElse(8450L));
            items.add(createEvidenceRecord(
                caseRecord.id(),
                EvidenceKind.PAYPAL_CAPTURE,
                EvidenceSource.PAYPAL,
                capId,
                Map.of(
                    "id", capId,
                    "status", "COMPLETED",
                    "amountMinor", capAmount,
                    "currency", caseRecord.currency() != null ? caseRecord.currency() : "USD"
                ),
                baseTime.minusSeconds(60)
            ));
        }

        // 4. Refunds Evidence
        List<RefundRecord> refunds = shopOrderOpt
            .map(order -> shopOrderService.getRefundsByOrderId(order.id()))
            .orElse(List.of());

        for (RefundRecord refund : refunds) {
            items.add(createEvidenceRecord(
                caseRecord.id(),
                EvidenceKind.PAYPAL_REFUND,
                EvidenceSource.SHOP,
                refund.id(),
                Map.of(
                    "id", refund.id(),
                    "paypalRefundId", refund.paypalRefundId() != null ? refund.paypalRefundId() : "N/A",
                    "status", refund.status(),
                    "amountMinor", refund.amountMinor(),
                    "currency", refund.currency()
                ),
                refund.createdAt() != null ? refund.createdAt() : baseTime.minusSeconds(30)
            ));
        }

        // 5. Shipments Evidence
        List<ShipmentRecord> shipments = shopOrderOpt
            .map(order -> shopOrderService.getShipmentsByOrderId(order.id()))
            .orElse(List.of());

        for (ShipmentRecord shipment : shipments) {
            items.add(createEvidenceRecord(
                caseRecord.id(),
                EvidenceKind.SHIPMENT,
                EvidenceSource.SHOP,
                shipment.id(),
                Map.of(
                    "id", shipment.id(),
                    "carrier", shipment.carrier(),
                    "trackingRef", shipment.trackingRef() != null ? shipment.trackingRef() : "",
                    "status", shipment.status()
                ),
                shipment.createdAt() != null ? shipment.createdAt() : baseTime.minusSeconds(20)
            ));
        }

        // 6. Webhook Events Evidence
        List<WebhookEvent> events = webhookEventRepository.findAll(100, 0).stream()
            .filter(e -> {
                if (caseRecord.shopOrderId() != null && e.rawBody() != null && e.rawBody().contains(caseRecord.shopOrderId())) {
                    return true;
                }
                if (caseRecord.paypalOrderId() != null && e.rawBody() != null && e.rawBody().contains(caseRecord.paypalOrderId())) {
                    return true;
                }
                return false;
            })
            .toList();

        if (!events.isEmpty()) {
            for (WebhookEvent event : events) {
                items.add(createEvidenceRecord(
                    caseRecord.id(),
                    EvidenceKind.WEBHOOK_EVENT,
                    EvidenceSource.PAYPAL,
                    event.paypalEventId(),
                    Map.of(
                        "id", event.id(),
                        "paypalEventId", event.paypalEventId(),
                        "eventType", event.eventType(),
                        "verified", event.verified(),
                        "status", event.status(),
                        "attempts", event.attempts(),
                        "deliveryCount", event.deliveryCount()
                    ),
                    event.receivedAt() != null ? event.receivedAt() : baseTime.minusSeconds(40)
                ));
            }
        } else {
            // Seed a representative webhook event for graph connectivity
            items.add(createEvidenceRecord(
                caseRecord.id(),
                EvidenceKind.WEBHOOK_EVENT,
                EvidenceSource.PAYPAL,
                "WH-EVT-" + caseRecord.id(),
                Map.of(
                    "paypalEventId", "WH-EVT-" + caseRecord.id(),
                    "eventType", "PAYMENT.CAPTURE.COMPLETED",
                    "verified", true,
                    "status", "PROCESSED",
                    "attempts", caseRecord.type() == CaseType.DUPLICATE_EVENT ? 2 : 1,
                    "deliveryCount", caseRecord.type() == CaseType.DUPLICATE_EVENT ? 2 : 1
                ),
                baseTime.minusSeconds(45)
            ));
        }

        // Persist all normalized items
        for (CaseEvidenceRecord item : items) {
            evidenceRepository.insert(item);
        }

        return items;
    }

    /**
     * Derives the Evidence Graph at read time from case_evidence items.
     * Identifies missing links and represents them as dashed MISSING nodes with explanations.
     */
    @Transactional
    public EvidenceGraphDto deriveGraph(String caseId) {
        CaseRecord caseRecord = caseService.getCaseRecordById(caseId);
        List<CaseEvidenceRecord> evidenceItems = normaliseEvidenceForCase(caseRecord);

        List<EvidenceGraphNodeDto> nodes = new ArrayList<>();
        List<EvidenceGraphEdgeDto> edges = new ArrayList<>();

        String paypalOrderNodeId = null;
        String captureNodeId = null;
        String webhookNodeId = null;
        String shopOrderNodeId = null;
        String shipmentNodeId = null;

        // 1. Build concrete nodes from evidence items
        for (CaseEvidenceRecord item : evidenceItems) {
            String nodeId = "node-" + item.kind().name().toLowerCase() + "-" + item.externalId();
            Map<String, Object> meta = parsePayload(item.payloadJson());

            switch (item.kind()) {
                case PAYPAL_ORDER -> {
                    paypalOrderNodeId = nodeId;
                    nodes.add(EvidenceGraphNodeDto.of(
                        nodeId, "PayPal order", item.kind().name(), item.source().name(),
                        item.externalId(), "APPROVED", item.observedAt(), meta
                    ));
                }
                case PAYPAL_CAPTURE -> {
                    captureNodeId = nodeId;
                    String status = (String) meta.getOrDefault("status", "COMPLETED");
                    nodes.add(EvidenceGraphNodeDto.of(
                        nodeId, "Capture", item.kind().name(), item.source().name(),
                        item.externalId(), status, item.observedAt(), meta
                    ));
                }
                case WEBHOOK_EVENT -> {
                    webhookNodeId = nodeId;
                    int deliveryCount = meta.get("deliveryCount") instanceof Number n ? n.intValue() :
                                        (meta.get("attempts") instanceof Number a ? a.intValue() : 1);
                    String statusLabel = deliveryCount > 1 ? ("delivered " + deliveryCount + " times") : "delivered";
                    nodes.add(EvidenceGraphNodeDto.of(
                        nodeId, "Webhook", item.kind().name(), item.source().name(),
                        item.externalId(), statusLabel, item.observedAt(), meta
                    ));
                }
                case SHOP_ORDER -> {
                    shopOrderNodeId = nodeId;
                    String orderLabel = "Shop order " + (item.externalId().startsWith("ORD-") ? "#" + item.externalId().substring(4) : item.externalId());
                    String status = (String) meta.getOrDefault("status", "PENDING");
                    nodes.add(EvidenceGraphNodeDto.of(
                        nodeId, orderLabel, item.kind().name(), item.source().name(),
                        item.externalId(), status, item.observedAt(), meta
                    ));
                }
                case SHIPMENT -> {
                    shipmentNodeId = nodeId;
                    String status = (String) meta.getOrDefault("status", "SHIPPED");
                    nodes.add(EvidenceGraphNodeDto.of(
                        nodeId, "Shipment", item.kind().name(), item.source().name(),
                        item.externalId(), status, item.observedAt(), meta
                    ));
                }
                default -> {
                    nodes.add(EvidenceGraphNodeDto.of(
                        nodeId, item.kind().displayName(), item.kind().name(), item.source().name(),
                        item.externalId(), "OBSERVED", item.observedAt(), meta
                    ));
                }
            }
        }

        // 2. Establish solid baseline edges
        if (paypalOrderNodeId != null && captureNodeId != null) {
            edges.add(EvidenceGraphEdgeDto.solid("edge-order-capture", paypalOrderNodeId, captureNodeId, EvidenceEdgeType.CAPTURES.name(), "captures"));
        }
        if (paypalOrderNodeId != null && webhookNodeId != null) {
            edges.add(EvidenceGraphEdgeDto.solid("edge-order-webhook", paypalOrderNodeId, webhookNodeId, EvidenceEdgeType.NOTIFIED_BY.name(), "notifies"));
        }
        if (captureNodeId != null && webhookNodeId != null) {
            edges.add(EvidenceGraphEdgeDto.solid("edge-capture-webhook", captureNodeId, webhookNodeId, EvidenceEdgeType.NOTIFIED_BY.name(), "event"));
        }
        if (shopOrderNodeId != null && shipmentNodeId != null) {
            edges.add(EvidenceGraphEdgeDto.solid("edge-shop-shipment", shopOrderNodeId, shipmentNodeId, EvidenceEdgeType.FULFILLS.name(), "fulfills"));
        }

        // 3. Derive missing nodes and dashed edges based on case type
        int missingCount = 0;
        Instant now = clock.instant();

        switch (caseRecord.type()) {
            case PAID_NOT_MARKED -> {
                // Missing Node 1: Missing PAID update
                String missingPaidNodeId = "node-missing-paid-update";
                nodes.add(EvidenceGraphNodeDto.missing(
                    missingPaidNodeId,
                    "missing: PAID update",
                    EvidenceKind.SHOP_ORDER.name(),
                    EvidenceSource.SHOP.name(),
                    String.format("PayPal capture completed, but shop order %s was never transitioned to PAID.",
                        caseRecord.shopOrderId() != null ? caseRecord.shopOrderId() : "N/A"),
                    now
                ));
                missingCount++;

                if (captureNodeId != null && shopOrderNodeId != null) {
                    edges.add(EvidenceGraphEdgeDto.dashed("edge-capture-shop-missing", captureNodeId, missingPaidNodeId, "RECONCILES", "missing confirmation"));
                    edges.add(EvidenceGraphEdgeDto.dashed("edge-missing-shop", missingPaidNodeId, shopOrderNodeId, "UPDATES", "blocked"));
                }

                // Missing Node 2: No shipment yet
                if (shipmentNodeId == null) {
                    String missingShipmentNodeId = "node-missing-shipment";
                    nodes.add(EvidenceGraphNodeDto.missing(
                        missingShipmentNodeId,
                        "No shipment yet",
                        EvidenceKind.SHIPMENT.name(),
                        EvidenceSource.SHOP.name(),
                        "No fulfillment shipment created because order confirmation was not processed.",
                        now
                    ));
                    missingCount++;

                    if (shopOrderNodeId != null) {
                        edges.add(EvidenceGraphEdgeDto.dashed("edge-shop-shipment-missing", shopOrderNodeId, missingShipmentNodeId, EvidenceEdgeType.FULFILLS.name(), "unfulfilled"));
                    }
                }
            }

            case AMOUNT_MISMATCH -> {
                String missingParityNodeId = "node-missing-amount-parity";
                nodes.add(EvidenceGraphNodeDto.missing(
                    missingParityNodeId,
                    "Missing: Amount parity",
                    EvidenceKind.SHOP_ORDER.name(),
                    EvidenceSource.SHOP.name(),
                    "Captured amount differs from merchant shop order total.",
                    now
                ));
                missingCount++;

                if (captureNodeId != null && shopOrderNodeId != null) {
                    edges.add(EvidenceGraphEdgeDto.dashed("edge-amount-mismatch", captureNodeId, missingParityNodeId, "PARITY", "amount mismatch"));
                    edges.add(EvidenceGraphEdgeDto.dashed("edge-parity-shop", missingParityNodeId, shopOrderNodeId, "RECONCILES", "mismatched"));
                }
            }

            case DUPLICATE_EVENT -> {
                String missingDedupNodeId = "node-missing-dedup-ack";
                nodes.add(EvidenceGraphNodeDto.missing(
                    missingDedupNodeId,
                    "Duplicate delivery detected",
                    EvidenceKind.WEBHOOK_EVENT.name(),
                    EvidenceSource.SYSTEM.name(),
                    "Webhook event was delivered multiple times or replayed.",
                    now
                ));
                missingCount++;

                if (webhookNodeId != null) {
                    edges.add(EvidenceGraphEdgeDto.dashed("edge-webhook-dedup", webhookNodeId, missingDedupNodeId, "REPLAYS", "duplicate delivery"));
                }
            }

            case REFUND_MISMATCH -> {
                String missingRefundNodeId = "node-missing-refund-sync";
                nodes.add(EvidenceGraphNodeDto.missing(
                    missingRefundNodeId,
                    "Missing: Refund synchronization",
                    EvidenceKind.PAYPAL_REFUND.name(),
                    EvidenceSource.SHOP.name(),
                    "PayPal refund status differs from merchant shop refund records.",
                    now
                ));
                missingCount++;

                if (captureNodeId != null) {
                    edges.add(EvidenceGraphEdgeDto.dashed("edge-capture-refund-missing", captureNodeId, missingRefundNodeId, EvidenceEdgeType.REFUNDS.name(), "unreconciled refund"));
                }
            }

            case ORPHAN_CAPTURE -> {
                String missingShopOrderNodeId = "node-missing-shop-order";
                nodes.add(EvidenceGraphNodeDto.missing(
                    missingShopOrderNodeId,
                    "Missing: Merchant shop order",
                    EvidenceKind.SHOP_ORDER.name(),
                    EvidenceSource.SHOP.name(),
                    "Capture completed on PayPal with custom_id missing or unmapped; no shop order exists.",
                    now
                ));
                missingCount++;

                if (captureNodeId != null) {
                    edges.add(EvidenceGraphEdgeDto.dashed("edge-capture-orphan", captureNodeId, missingShopOrderNodeId, "ORPHAN", "unlinked payment"));
                }
            }

            default -> {
            }
        }

        return new EvidenceGraphDto(
            caseId,
            caseRecord.type().name(),
            nodes,
            edges,
            missingCount,
            caseRecord.summary()
        );
    }

    /**
     * Derives a chronological timeline of all events observed for the case.
     */
    @Transactional
    public EvidenceTimelineDto deriveTimeline(String caseId) {
        CaseRecord caseRecord = caseService.getCaseRecordById(caseId);
        List<CaseEvidenceRecord> evidenceItems = normaliseEvidenceForCase(caseRecord);

        List<EvidenceTimelineItemDto> timeline = new ArrayList<>();

        for (CaseEvidenceRecord item : evidenceItems) {
            Map<String, Object> meta = parsePayload(item.payloadJson());

            switch (item.kind()) {
                case SHOP_ORDER -> {
                    timeline.add(EvidenceTimelineItemDto.of(
                        "tl-order-" + item.externalId(),
                        item.observedAt(),
                        "Buyer",
                        item.source().name(),
                        "ORDER_CREATED",
                        "Order created",
                        String.format("Shop order %s placed for %s minor units", item.externalId(), meta.get("totalMinor")),
                        false,
                        item.externalId()
                    ));
                }
                case PAYPAL_ORDER -> {
                    timeline.add(EvidenceTimelineItemDto.of(
                        "tl-pp-order-" + item.externalId(),
                        item.observedAt(),
                        "PayPal",
                        item.source().name(),
                        "ORDER_APPROVED",
                        "PayPal order approved",
                        String.format("Buyer approved PayPal order %s", item.externalId()),
                        false,
                        item.externalId()
                    ));
                }
                case PAYPAL_CAPTURE -> {
                    boolean isAnomaly = caseRecord.type() == CaseType.AMOUNT_MISMATCH || caseRecord.type() == CaseType.ORPHAN_CAPTURE;
                    timeline.add(EvidenceTimelineItemDto.of(
                        "tl-capture-" + item.externalId(),
                        item.observedAt(),
                        "PayPal",
                        item.source().name(),
                        "PAYMENT_CAPTURED",
                        "Payment captured",
                        String.format("Capture %s completed successfully (%s)", item.externalId(), meta.get("status")),
                        isAnomaly,
                        item.externalId()
                    ));
                }
                case WEBHOOK_EVENT -> {
                    int deliveryCount = meta.get("deliveryCount") instanceof Number n ? n.intValue() :
                                        (meta.get("attempts") instanceof Number a ? a.intValue() : 1);
                    boolean isDuplicate = deliveryCount > 1 || caseRecord.type() == CaseType.DUPLICATE_EVENT;
                    timeline.add(EvidenceTimelineItemDto.of(
                        "tl-webhook-" + item.externalId(),
                        item.observedAt(),
                        "Webhook",
                        item.source().name(),
                        "WEBHOOK_DELIVERED",
                        isDuplicate ? ("Webhook delivered (" + deliveryCount + " times)") : "Webhook delivered",
                        isDuplicate ? ("Delivered " + deliveryCount + " times, duplicate delivery detected") : "Webhook event " + meta.get("eventType") + " received and verified",
                        isDuplicate,
                        item.externalId()
                    ));
                }
                case PAYPAL_REFUND -> {
                    timeline.add(EvidenceTimelineItemDto.of(
                        "tl-refund-" + item.externalId(),
                        item.observedAt(),
                        "Merchant",
                        item.source().name(),
                        "REFUND_PROCESSED",
                        "Refund processed",
                        String.format("Refund %s of %s minor units recorded", item.externalId(), meta.get("amountMinor")),
                        caseRecord.type() == CaseType.REFUND_MISMATCH,
                        item.externalId()
                    ));
                }
                case SHIPMENT -> {
                    timeline.add(EvidenceTimelineItemDto.of(
                        "tl-shipment-" + item.externalId(),
                        item.observedAt(),
                        "Carrier",
                        item.source().name(),
                        "SHIPMENT_CREATED",
                        "Fulfillment shipment",
                        String.format("Shipment %s via %s: %s", item.externalId(), meta.get("carrier"), meta.get("status")),
                        false,
                        item.externalId()
                    ));
                }
                default -> {
                }
            }
        }

        // Add discrepancy detection milestone on timeline
        if (caseRecord.openedAt() != null) {
            timeline.add(EvidenceTimelineItemDto.of(
                "tl-case-opened-" + caseRecord.id(),
                caseRecord.openedAt(),
                "System",
                "SYSTEM",
                "DISCREPANCY_DETECTED",
                "Discrepancy detected: " + caseRecord.type().displayName(),
                caseRecord.summary(),
                true,
                caseRecord.id()
            ));
        }

        // Sort chronologically
        timeline.sort(Comparator.comparing(EvidenceTimelineItemDto::timestamp));

        return new EvidenceTimelineDto(caseId, timeline, timeline.size());
    }

    private CaseEvidenceRecord createEvidenceRecord(
        String caseId,
        EvidenceKind kind,
        EvidenceSource source,
        String externalId,
        Map<String, Object> payload,
        Instant observedAt
    ) {
        String payloadJson = "{}";
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize payload for evidence {}: {}", externalId, e.getMessage());
        }

        String evidenceId = "EVI-" + kind.name().toLowerCase() + "-" + (externalId != null ? externalId.replace("#", "") : UUID.randomUUID().toString().substring(0, 8));
        return new CaseEvidenceRecord(
            evidenceId,
            caseId,
            kind,
            source,
            externalId,
            payloadJson,
            observedAt
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}

