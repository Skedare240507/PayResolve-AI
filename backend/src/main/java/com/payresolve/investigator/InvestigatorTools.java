package com.payresolve.investigator;

import com.payresolve.evidence.CaseEvidenceRecord;
import com.payresolve.evidence.CaseEvidenceRepository;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.ingest.WebhookEventRepository;
import com.payresolve.investigator.model.InvestigationStepRecord;
import com.payresolve.paypal.PayPalDisputesClient;
import com.payresolve.paypal.PayPalOrdersClient;
import com.payresolve.paypal.PayPalPaymentsClient;
import com.payresolve.paypal.model.CaptureDetails;
import com.payresolve.paypal.model.DisputeDetail;
import com.payresolve.paypal.model.PayPalOrder;
import com.payresolve.paypal.model.RefundDetails;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShipmentRecord;
import com.payresolve.shop.ShopOrder;
import com.payresolve.shop.ShopOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Read-only tool definitions for the AI Investigator agent.
 * Under strict money-safety constraints, NO tools can modify state, balances, or move money.
 * Every tool invocation is logged into `investigation_steps` with arguments and execution latency.
 */
@Component
public class InvestigatorTools {

    private static final Logger log = LoggerFactory.getLogger(InvestigatorTools.class);
    private static final int MAX_STEPS_PER_INVESTIGATION = 10;

    private final PayPalOrdersClient payPalOrdersClient;
    private final PayPalPaymentsClient payPalPaymentsClient;
    private final PayPalDisputesClient payPalDisputesClient;
    private final ShopOrderService shopOrderService;
    private final WebhookEventRepository webhookEventRepository;
    private final CaseEvidenceRepository caseEvidenceRepository;
    private final InvestigationStepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final ConcurrentHashMap<String, AtomicInteger> investigationStepCounters = new ConcurrentHashMap<>();

    public InvestigatorTools(
        PayPalOrdersClient payPalOrdersClient,
        PayPalPaymentsClient payPalPaymentsClient,
        PayPalDisputesClient payPalDisputesClient,
        ShopOrderService shopOrderService,
        WebhookEventRepository webhookEventRepository,
        CaseEvidenceRepository caseEvidenceRepository,
        InvestigationStepRepository stepRepository,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.payPalOrdersClient = payPalOrdersClient;
        this.payPalPaymentsClient = payPalPaymentsClient;
        this.payPalDisputesClient = payPalDisputesClient;
        this.shopOrderService = shopOrderService;
        this.webhookEventRepository = webhookEventRepository;
        this.caseEvidenceRepository = caseEvidenceRepository;
        this.stepRepository = stepRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void resetStepCounter(String investigationId) {
        if (investigationId != null) {
            investigationStepCounters.put(investigationId, new AtomicInteger(0));
        }
    }

    private boolean checkStepCap(String investigationId) {
        if (investigationId == null) {
            return false;
        }
        AtomicInteger counter = investigationStepCounters.computeIfAbsent(investigationId, k -> new AtomicInteger(0));
        return counter.incrementAndGet() > MAX_STEPS_PER_INVESTIGATION;
    }

    @Tool(name = "getPaypalOrder", description = "Retrieves live details of a PayPal Order by its PayPal Order ID.")
    public String getPaypalOrder(
        @ToolParam(description = "PayPal Order ID, e.g. PP-10482", required = true) String paypalOrderId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("getPaypalOrder", Map.of("paypalOrderId", paypalOrderId),
                "{\"error\": \"STEP_CAP_REACHED\", \"message\": \"Maximum investigation steps exceeded\"}", 1);
        }

        try {
            PayPalOrder order = payPalOrdersClient.getOrder(paypalOrderId);
            String result = toJson(order);
            String digest = String.format("PayPal order %s: status=%s, intent=%s",
                order.id(), order.status(), order.intent());
            return logAndReturn("getPaypalOrder", Map.of("paypalOrderId", paypalOrderId), result, digest, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("getPaypalOrder failed for {}: {}", paypalOrderId, e.getMessage());
            // Fallback to checking local shop order's PayPal ID
            Optional<ShopOrder> localOrder = shopOrderService.findOrderByPayPalOrderId(paypalOrderId);
            if (localOrder.isPresent()) {
                String digest = "PayPal order found in local cache: " + paypalOrderId;
                String payload = toJson(Map.of("id", paypalOrderId, "status", "APPROVED", "shopOrderId", localOrder.get().id()));
                return logAndReturn("getPaypalOrder", Map.of("paypalOrderId", paypalOrderId), payload, digest, System.currentTimeMillis() - start);
            }
            String notFound = "{\"status\": \"NOT_FOUND\", \"orderId\": \"" + paypalOrderId + "\"}";
            return logAndReturn("getPaypalOrder", Map.of("paypalOrderId", paypalOrderId), notFound, "PayPal order not found: " + paypalOrderId, System.currentTimeMillis() - start);
        }
    }

    @Tool(name = "getCaptureDetails", description = "Retrieves details of a PayPal Payment Capture transaction.")
    public String getCaptureDetails(
        @ToolParam(description = "PayPal Capture ID, e.g. CAP-10482", required = true) String captureId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("getCaptureDetails", Map.of("captureId", captureId),
                "{\"error\": \"STEP_CAP_REACHED\"}", 1);
        }

        try {
            CaptureDetails capture = payPalPaymentsClient.getCapture(captureId);
            String result = toJson(capture);
            String digest = String.format("Capture %s: status=%s, amount=%s",
                capture.id(), capture.status(), capture.amount() != null ? capture.amount().value() + " " + capture.amount().currencyCode() : "N/A");
            return logAndReturn("getCaptureDetails", Map.of("captureId", captureId), result, digest, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("getCaptureDetails live lookup failed for {}: {}", captureId, e.getMessage());
            // Fallback to local payment records
            String numericId = captureId.replace("CAP-", "").trim();
            Optional<PaymentRecord> paymentOpt = shopOrderService.findPaymentByOrderId(numericId);
            if (paymentOpt.isPresent()) {
                PaymentRecord p = paymentOpt.get();
                String digest = String.format("Payment capture record: %s, amount=%s, status=%s", p.paypalCaptureId(), p.amountMinor(), p.status());
                return logAndReturn("getCaptureDetails", Map.of("captureId", captureId), toJson(p), digest, System.currentTimeMillis() - start);
            }
            String notFound = "{\"status\": \"NOT_FOUND\", \"captureId\": \"" + captureId + "\"}";
            return logAndReturn("getCaptureDetails", Map.of("captureId", captureId), notFound, "Capture not found: " + captureId, System.currentTimeMillis() - start);
        }
    }

    @Tool(name = "getRefundDetails", description = "Retrieves details of a PayPal Refund transaction.")
    public String getRefundDetails(
        @ToolParam(description = "Refund ID or PayPal Refund ID", required = true) String refundId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("getRefundDetails", Map.of("refundId", refundId),
                "{\"error\": \"STEP_CAP_REACHED\"}", 1);
        }

        try {
            RefundDetails refund = payPalPaymentsClient.getRefund(refundId);
            String result = toJson(refund);
            String digest = String.format("PayPal refund %s: status=%s", refund.id(), refund.status());
            return logAndReturn("getRefundDetails", Map.of("refundId", refundId), result, digest, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("getRefundDetails live lookup failed for {}: {}", refundId, e.getMessage());
            String notFound = "{\"status\": \"NOT_FOUND\", \"refundId\": \"" + refundId + "\"}";
            return logAndReturn("getRefundDetails", Map.of("refundId", refundId), notFound, "Refund record not found: " + refundId, System.currentTimeMillis() - start);
        }
    }

    @Tool(name = "listWebhookEventsForOrder", description = "Lists all received PayPal webhook events mentioning the given order or capture ID.")
    public String listWebhookEventsForOrder(
        @ToolParam(description = "Order ID or Capture ID to search webhooks for", required = true) String orderId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("listWebhookEventsForOrder", Map.of("orderId", orderId),
                "{\"error\": \"STEP_CAP_REACHED\"}", 1);
        }

        List<WebhookEvent> matchingEvents = webhookEventRepository.findAll(100, 0).stream()
            .filter(e -> e.rawBody() != null && e.rawBody().contains(orderId))
            .toList();

        List<Map<String, Object>> summaries = matchingEvents.stream()
            .map(e -> Map.<String, Object>of(
                "id", e.id(),
                "paypalEventId", e.paypalEventId(),
                "eventType", e.eventType(),
                "status", e.status(),
                "verified", e.verified(),
                "attempts", e.attempts(),
                "receivedAt", e.receivedAt() != null ? e.receivedAt().toString() : ""
            ))
            .toList();

        String result = toJson(summaries);
        String digest = String.format("Found %d webhook events for %s", summaries.size(), orderId);
        return logAndReturn("listWebhookEventsForOrder", Map.of("orderId", orderId), result, digest, System.currentTimeMillis() - start);
    }

    @Tool(name = "getShopOrder", description = "Retrieves merchant shop order details by shop order ID.")
    public String getShopOrder(
        @ToolParam(description = "Merchant Shop Order ID, e.g. ORD-10482", required = true) String shopOrderId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("getShopOrder", Map.of("shopOrderId", shopOrderId),
                "{\"error\": \"STEP_CAP_REACHED\"}", 1);
        }

        Optional<ShopOrder> orderOpt = shopOrderService.findOrderById(shopOrderId);
        if (orderOpt.isPresent()) {
            ShopOrder order = orderOpt.get();
            String result = toJson(order);
            String digest = String.format("Shop order %s: status=%s, total=%s minor units %s",
                order.id(), order.status(), order.totalMinor(), order.currency());
            return logAndReturn("getShopOrder", Map.of("shopOrderId", shopOrderId), result, digest, System.currentTimeMillis() - start);
        }

        String notFound = "{\"status\": \"NOT_FOUND\", \"shopOrderId\": \"" + shopOrderId + "\"}";
        return logAndReturn("getShopOrder", Map.of("shopOrderId", shopOrderId), notFound, "Shop order not found: " + shopOrderId, System.currentTimeMillis() - start);
    }

    @Tool(name = "getShipment", description = "Retrieves shipment fulfillment and tracking details for a merchant shop order.")
    public String getShipment(
        @ToolParam(description = "Merchant Shop Order ID, e.g. ORD-10482", required = true) String shopOrderId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("getShipment", Map.of("shopOrderId", shopOrderId),
                "{\"error\": \"STEP_CAP_REACHED\"}", 1);
        }

        List<ShipmentRecord> shipments = shopOrderService.getShipmentsByOrderId(shopOrderId);
        String result = toJson(shipments);
        String digest = shipments.isEmpty() ? "No shipment records found for " + shopOrderId
            : String.format("Found %d shipment(s) for %s: carrier=%s, tracking=%s",
                shipments.size(), shopOrderId, shipments.get(0).carrier(), shipments.get(0).trackingRef());
        return logAndReturn("getShipment", Map.of("shopOrderId", shopOrderId), result, digest, System.currentTimeMillis() - start);
    }

    @Tool(name = "getCaseEvidence", description = "Retrieves all normalized evidence items collected for an exception case.")
    public String getCaseEvidence(
        @ToolParam(description = "Case ID, e.g. PR-0142", required = true) String caseId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("getCaseEvidence", Map.of("caseId", caseId),
                "{\"error\": \"STEP_CAP_REACHED\"}", 1);
        }

        List<CaseEvidenceRecord> evidenceList = caseEvidenceRepository.findByCaseId(caseId);
        List<Map<String, Object>> summaries = evidenceList.stream()
            .map(e -> Map.<String, Object>of(
                "id", e.id(),
                "kind", e.kind().name(),
                "source", e.source().name(),
                "externalId", e.externalId() != null ? e.externalId() : "",
                "observedAt", e.observedAt() != null ? e.observedAt().toString() : ""
            ))
            .toList();

        String result = toJson(summaries);
        String digest = String.format("Retrieved %d normalized evidence items for case %s", summaries.size(), caseId);
        return logAndReturn("getCaseEvidence", Map.of("caseId", caseId), result, digest, System.currentTimeMillis() - start);
    }

    @Tool(name = "getDisputeIfAny", description = "Retrieves customer dispute details if any dispute exists for the transaction or case.")
    public String getDisputeIfAny(
        @ToolParam(description = "Dispute ID or Case ID", required = true) String disputeIdOrCaseId
    ) {
        long start = System.currentTimeMillis();
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (checkStepCap(invId)) {
            return logAndReturn("getDisputeIfAny", Map.of("disputeIdOrCaseId", disputeIdOrCaseId),
                "{\"error\": \"STEP_CAP_REACHED\"}", 1);
        }

        try {
            DisputeDetail dispute = payPalDisputesClient.getDispute(disputeIdOrCaseId);
            String result = toJson(dispute);
            String digest = String.format("Dispute %s: status=%s, reason=%s", dispute.disputeId(), dispute.status(), dispute.reason());
            return logAndReturn("getDisputeIfAny", Map.of("disputeIdOrCaseId", disputeIdOrCaseId), result, digest, System.currentTimeMillis() - start);
        } catch (Exception e) {
            String notFound = "{\"status\": \"NO_DISPUTES_FOUND\", \"query\": \"" + disputeIdOrCaseId + "\"}";
            return logAndReturn("getDisputeIfAny", Map.of("disputeIdOrCaseId", disputeIdOrCaseId), notFound, "No dispute found for " + disputeIdOrCaseId, System.currentTimeMillis() - start);
        }
    }

    private String logAndReturn(String tool, Map<String, Object> args, String result, long ms) {
        return logAndReturn(tool, args, result, result.length() > 100 ? result.substring(0, 100) + "..." : result, ms);
    }

    private String logAndReturn(String tool, Map<String, Object> args, String result, String digest, long ms) {
        String invId = InvestigationContext.getCurrentInvestigationId();
        if (invId != null) {
            try {
                String argsJson = objectMapper.writeValueAsString(args);
                stepRepository.insert(new InvestigationStepRecord(
                    null,
                    invId,
                    tool,
                    argsJson,
                    digest,
                    clock.instant(),
                    ms
                ));
            } catch (Exception e) {
                log.warn("Failed to record tool step for {}: {}", tool, e.getMessage());
            }
        }
        return result;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

