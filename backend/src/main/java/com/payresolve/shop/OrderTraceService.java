package com.payresolve.shop;

import com.payresolve.cases.CaseService;
import com.payresolve.cases.dto.CaseDetailsDto;
import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.ingest.EventQueryService;
import com.payresolve.ingest.dto.OrderWebhookTraceDto;
import com.payresolve.shop.dto.BuyerOrderStatusDto;
import com.payresolve.shop.dto.OrderTraceDto;
import com.payresolve.shop.dto.OrderTraceStepDto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Builds the end-to-end payment trace timeline for a shop order across
 * merchant state, PayPal order/capture timestamps, webhook inbox processing,
 * and opened exception cases.
 */
@Service
public class OrderTraceService {

    private final ShopOrderService shopOrderService;
    private final EventQueryService eventQueryService;
    private final CaseService caseService;
    private final Clock clock;

    public OrderTraceService(
        ShopOrderService shopOrderService,
        EventQueryService eventQueryService,
        CaseService caseService,
        Clock clock
    ) {
        this.shopOrderService = shopOrderService;
        this.eventQueryService = eventQueryService;
        this.caseService = caseService;
        this.clock = clock;
    }

    /**
     * Public minimal buyer return status check gated by the PayPal checkout token.
     */
    public BuyerOrderStatusDto getBuyerOrderStatus(String orderId, String token) {
        ShopOrder order = shopOrderService.findOrderById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + orderId));
        if (token == null || token.isBlank() || order.paypalOrderId() == null || !order.paypalOrderId().equals(token.trim())) {
            throw new AccessDeniedException("Invalid PayPal checkout token for order " + orderId);
        }
        OrderTraceDto trace = getOrderTrace(orderId);
        return new BuyerOrderStatusDto(
            trace.orderStatus(),
            trace.totalMinor(),
            trace.currency(),
            order.total().format(),
            trace.steps()
        );
    }

    /**
     * Builds the ordered 8-step timeline for a single shop order.
     */
    public OrderTraceDto getOrderTrace(String orderId) {
        ShopOrder order = shopOrderService.findOrderById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shop order not found: " + orderId));

        Optional<PaymentRecord> paymentOpt = shopOrderService.findPaymentByOrderId(order.id());
        String captureId = resolveCaptureId(order, paymentOpt);

        List<OrderWebhookTraceDto> events = eventQueryService.findEventsForOrder(
            order.id(),
            order.paypalOrderId(),
            captureId
        );
        List<CaseDetailsDto> cases = caseService.findCasesForOrder(order.id(), order.paypalOrderId());

        Optional<OrderWebhookTraceDto> approvedEvent = findLatestEventByType(events, "CHECKOUT.ORDER.APPROVED");
        Optional<OrderWebhookTraceDto> captureEvent = findCaptureWebhookEvent(events);

        Instant paypalCreatedAt = resolvePaypalCreatedAt(order);
        Instant buyerApprovedAt = resolveBuyerApprovedAt(order, approvedEvent, paymentOpt);
        Instant capturedAt = resolveCapturedAt(order, paymentOpt);
        boolean isPaid = order.isPaid() || "REFUNDED".equalsIgnoreCase(order.status());
        Instant paidAt = isPaid
            ? firstNonNull(order.paidAt(), paymentOpt.map(PaymentRecord::capturedAt).orElse(null), order.updatedAt())
            : null;

        OrderTraceStepDto stepOrderCreated = step(
            "ORDER_CREATED",
            "Order created",
            "COMPLETED",
            true,
            order.createdAt(),
            String.format("Shop order %s created for %s.", order.id(), order.total()),
            order.id(),
            "The demo shop persisted the order in PostgreSQL with status PENDING using minor-unit item totals."
        );

        boolean hasPaypalOrder = order.paypalOrderId() != null && !order.paypalOrderId().isBlank();
        OrderTraceStepDto stepPaypalOrderCreated = step(
            "PAYPAL_ORDER_CREATED",
            "PayPal order created",
            hasPaypalOrder ? "COMPLETED" : "PENDING",
            hasPaypalOrder,
            paypalCreatedAt,
            hasPaypalOrder
                ? String.format("PayPal order %s created via Orders v2 API.", order.paypalOrderId())
                : "PayPal checkout has not been initiated yet.",
            order.paypalOrderId(),
            "PayResolve called POST /v2/checkout/orders with custom_id set to the shop order ID and received an approval URL."
        );

        OrderTraceStepDto stepBuyerApproved = step(
            "BUYER_APPROVED",
            "Buyer approved",
            buyerApprovedAt != null ? "COMPLETED" : "PENDING",
            buyerApprovedAt != null,
            buyerApprovedAt,
            buyerApprovedAt != null
                ? "Buyer authorized payment in PayPal Sandbox checkout."
                : "Waiting for the buyer to approve payment in PayPal Sandbox.",
            order.paypalOrderId(),
            "The sandbox buyer signed in to PayPal and approved the payment before returning to /shop/return."
        );

        OrderTraceStepDto stepCapturedApi = step(
            "CAPTURED_API",
            "Captured (API)",
            capturedAt != null ? "COMPLETED" : "PENDING",
            capturedAt != null,
            capturedAt,
            capturedAt != null
                ? String.format("Server-side capture succeeded%s.", captureId != null ? " (Capture ID: " + captureId + ")" : "")
                : "Server-side capture has not executed yet.",
            captureId,
            "PayResolve called POST /v2/checkout/orders/{id}/capture. By design this moves the order to AWAITING_CONFIRMATION until the webhook confirms."
        );

        OrderTraceStepDto stepWebhookReceived = buildWebhookReceivedStep(captureEvent, approvedEvent);
        OrderTraceStepDto stepWebhookProcessed = buildWebhookProcessedStep(captureEvent);

        OrderTraceStepDto stepOrderPaid = step(
            "ORDER_MARKED_PAID",
            "Shop order marked PAID",
            isPaid ? "COMPLETED" : "PENDING",
            isPaid,
            paidAt,
            isPaid
                ? String.format("Shop order status transitioned to %s.", order.status())
                : String.format("Shop order remains %s until verified capture webhook processing finishes.", order.status()),
            order.id(),
            "Only the verified webhook inbox worker marks the merchant order PAID so missing or broken webhooks are never hidden."
        );

        OrderTraceStepDto stepCaseOpened = buildCaseOpenedStep(cases);
        String linkedCaseId = cases.isEmpty() ? null : cases.getFirst().id();

        return new OrderTraceDto(
            order.id(),
            order.paypalOrderId(),
            order.status(),
            order.totalMinor(),
            order.currency(),
            captureId,
            linkedCaseId,
            clock.instant(),
            List.of(
                stepOrderCreated,
                stepPaypalOrderCreated,
                stepBuyerApproved,
                stepCapturedApi,
                stepWebhookReceived,
                stepWebhookProcessed,
                stepOrderPaid,
                stepCaseOpened
            )
        );
    }

    private OrderTraceStepDto buildWebhookReceivedStep(
        Optional<OrderWebhookTraceDto> captureEvent,
        Optional<OrderWebhookTraceDto> approvedEvent
    ) {
        if (captureEvent.isEmpty()) {
            String detail = approvedEvent.isPresent()
                ? "CHECKOUT.ORDER.APPROVED arrived, but PAYMENT.CAPTURE.COMPLETED has not arrived yet."
                : "No PAYMENT.CAPTURE.COMPLETED webhook received yet.";
            return step(
                "WEBHOOK_RECEIVED",
                "Webhook received",
                "PENDING",
                false,
                null,
                detail,
                null,
                "PayPal delivers PAYMENT.CAPTURE.COMPLETED to /api/webhooks/paypal, where PayResolve verifies the transmission signature."
            );
        }

        OrderWebhookTraceDto ev = captureEvent.get();
        boolean verified = ev.verified();
        return step(
            "WEBHOOK_RECEIVED",
            "Webhook received",
            verified ? "VERIFIED" : "UNVERIFIED",
            true,
            ev.receivedAt(),
            verified
                ? String.format("Verified webhook %s (%s) stored in durable inbox.", ev.paypalEventId(), ev.eventType())
                : String.format("Unverified webhook %s (%s) failed signature check; will not be processed.", ev.paypalEventId(), ev.eventType()),
            ev.paypalEventId(),
            "PayPal delivers PAYMENT.CAPTURE.COMPLETED to /api/webhooks/paypal, where PayResolve verifies the transmission signature."
        );
    }

    private OrderTraceStepDto buildWebhookProcessedStep(Optional<OrderWebhookTraceDto> captureEvent) {
        String explanation = "The background worker claims verified PENDING events with FOR UPDATE SKIP LOCKED and applies the capture idempotently.";
        if (captureEvent.isEmpty()) {
            return step(
                "WEBHOOK_PROCESSED",
                "Webhook processed",
                "PENDING",
                false,
                null,
                "Waiting for a capture webhook to arrive before worker processing.",
                null,
                explanation
            );
        }

        OrderWebhookTraceDto ev = captureEvent.get();
        if ("PROCESSED".equalsIgnoreCase(ev.status())) {
            return step(
                "WEBHOOK_PROCESSED",
                "Webhook processed",
                "COMPLETED",
                true,
                ev.processedAt(),
                String.format("Worker processed webhook %s after %d attempt(s).", ev.paypalEventId(), Math.max(1, ev.attempts())),
                ev.paypalEventId(),
                explanation
            );
        }

        if (!ev.verified()) {
            return step(
                "WEBHOOK_PROCESSED",
                "Webhook processed",
                "UNVERIFIED",
                false,
                null,
                "Blocked: unverified webhook events are never processed by the worker.",
                ev.paypalEventId(),
                explanation
            );
        }

        if ("FAILED".equalsIgnoreCase(ev.status()) || (ev.lastError() != null && !ev.lastError().isBlank())) {
            String errText = ev.lastError() != null ? ev.lastError() : "Worker failed to process webhook";
            String status = "PENDING".equalsIgnoreCase(ev.status()) ? "RETRYING" : "FAILED";
            return step(
                "WEBHOOK_PROCESSED",
                "Webhook processed",
                status,
                false,
                null,
                String.format("Worker error (attempt %d): %s", ev.attempts(), errText),
                ev.paypalEventId(),
                explanation
            );
        }

        return step(
            "WEBHOOK_PROCESSED",
            "Webhook processed",
            "PENDING",
            false,
            null,
            "Webhook is queued (PENDING) for background worker execution.",
            ev.paypalEventId(),
            explanation
        );
    }

    private OrderTraceStepDto buildCaseOpenedStep(List<CaseDetailsDto> cases) {
        String explanation = "Rule-based detectors inspect orders, captures, and webhooks and open an exception case when an invariant breaks.";
        if (cases.isEmpty()) {
            return step(
                "CASE_OPENED",
                "Case opened (if any)",
                "NONE",
                false,
                null,
                "No discrepancy case opened for this order.",
                null,
                explanation
            );
        }

        CaseDetailsDto c = cases.getFirst();
        return step(
            "CASE_OPENED",
            "Case opened (if any)",
            "OPENED",
            true,
            c.openedAt(),
            String.format("Case %s (%s · %s): %s", c.id(), c.typeLabel(), c.status(), c.summary()),
            c.id(),
            explanation
        );
    }

    private static OrderTraceStepDto step(
        String id,
        String label,
        String status,
        boolean completed,
        Instant timestamp,
        String detail,
        String externalRef,
        String explanation
    ) {
        return new OrderTraceStepDto(
            id,
            label,
            status,
            completed,
            timestamp,
            timestamp != null ? timestamp.toString() : "not yet",
            detail,
            externalRef,
            explanation
        );
    }

    private static String resolveCaptureId(ShopOrder order, Optional<PaymentRecord> paymentOpt) {
        if (order.paypalCaptureId() != null && !order.paypalCaptureId().isBlank()) {
            return order.paypalCaptureId();
        }
        return paymentOpt.map(PaymentRecord::paypalCaptureId).orElse(null);
    }

    private static Instant resolvePaypalCreatedAt(ShopOrder order) {
        if (order.paypalOrderId() == null || order.paypalOrderId().isBlank()) {
            return null;
        }
        return order.paypalOrderCreatedAt() != null ? order.paypalOrderCreatedAt() : order.createdAt();
    }

    private static Instant resolveBuyerApprovedAt(
        ShopOrder order,
        Optional<OrderWebhookTraceDto> approvedEvent,
        Optional<PaymentRecord> paymentOpt
    ) {
        if (order.buyerApprovedAt() != null) {
            return order.buyerApprovedAt();
        }
        if (approvedEvent.isPresent()) {
            OrderWebhookTraceDto ev = approvedEvent.get();
            return ev.eventCreatedAt() != null ? ev.eventCreatedAt() : ev.receivedAt();
        }
        if (order.capturedAt() != null) {
            return order.capturedAt();
        }
        if (order.isAwaitingConfirmation() || order.isPaid() || "REFUNDED".equalsIgnoreCase(order.status())) {
            return paymentOpt.map(PaymentRecord::capturedAt).orElse(order.updatedAt());
        }
        return null;
    }

    private static Instant resolveCapturedAt(ShopOrder order, Optional<PaymentRecord> paymentOpt) {
        if (order.capturedAt() != null) {
            return order.capturedAt();
        }
        if (order.isAwaitingConfirmation() || order.isPaid() || "REFUNDED".equalsIgnoreCase(order.status())) {
            return paymentOpt.map(PaymentRecord::capturedAt).orElse(order.updatedAt());
        }
        return paymentOpt.map(PaymentRecord::capturedAt).orElse(null);
    }

    private static Optional<OrderWebhookTraceDto> findLatestEventByType(List<OrderWebhookTraceDto> events, String eventType) {
        return events.stream()
            .filter(e -> eventType.equalsIgnoreCase(e.eventType()))
            .reduce((first, second) -> second);
    }

    private static Optional<OrderWebhookTraceDto> findCaptureWebhookEvent(List<OrderWebhookTraceDto> events) {
        Optional<OrderWebhookTraceDto> completed = findLatestEventByType(events, "PAYMENT.CAPTURE.COMPLETED");
        if (completed.isPresent()) {
            return completed;
        }
        Optional<OrderWebhookTraceDto> denied = findLatestEventByType(events, "PAYMENT.CAPTURE.DENIED");
        if (denied.isPresent()) {
            return denied;
        }
        return events.stream()
            .filter(e -> e.eventType() != null && e.eventType().startsWith("PAYMENT.CAPTURE."))
            .reduce((first, second) -> second);
    }

    private static Instant firstNonNull(Instant a, Instant b, Instant c) {
        if (a != null) return a;
        if (b != null) return b;
        return c;
    }
}

