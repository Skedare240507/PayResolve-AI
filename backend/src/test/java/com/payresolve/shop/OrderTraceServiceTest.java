package com.payresolve.shop;

import com.payresolve.cases.CaseService;
import com.payresolve.cases.dto.CaseDetailsDto;
import com.payresolve.ingest.EventQueryService;
import com.payresolve.ingest.dto.OrderWebhookTraceDto;
import com.payresolve.shop.dto.OrderTraceDto;
import com.payresolve.shop.dto.OrderTraceStepDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTraceServiceTest {

    @Mock
    private ShopOrderService shopOrderService;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private CaseService caseService;

    private Clock fixedClock;
    private OrderTraceService orderTraceService;
    private final Instant t0 = Instant.parse("2026-11-11T12:00:00Z");

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(t0.plusSeconds(120), ZoneOffset.UTC);
        orderTraceService = new OrderTraceService(shopOrderService, eventQueryService, caseService, fixedClock);
    }

    @Test
    @DisplayName("1. Fresh PENDING order shows Order created with timestamp and remaining steps as 'not yet'")
    void pendingOrderShowsOnlyOrderCreated() {
        ShopOrder pending = new ShopOrder("ORD-100", null, "PENDING", 2400L, "USD", "Buyer", t0, t0);
        when(shopOrderService.findOrderById("ORD-100")).thenReturn(Optional.of(pending));
        when(shopOrderService.findPaymentByOrderId("ORD-100")).thenReturn(Optional.empty());
        when(eventQueryService.findEventsForOrder("ORD-100", null, null)).thenReturn(List.of());
        when(caseService.findCasesForOrder("ORD-100", null)).thenReturn(List.of());

        OrderTraceDto trace = orderTraceService.getOrderTrace("ORD-100");

        assertThat(trace.orderId()).isEqualTo("ORD-100");
        assertThat(trace.steps()).hasSize(8);

        assertThat(trace.steps().get(0).id()).isEqualTo("ORDER_CREATED");
        assertThat(trace.steps().get(0).completed()).isTrue();
        assertThat(trace.steps().get(0).timestamp()).isEqualTo(t0);

        for (int i = 1; i < 8; i++) {
            OrderTraceStepDto step = trace.steps().get(i);
            assertThat(step.completed()).isFalse();
            assertThat(step.timestamp()).isNull();
            assertThat(step.timestampLabel()).isEqualTo("not yet");
            assertThat(step.explanation()).isNotBlank();
        }
    }

    @Test
    @DisplayName("2. Captured order awaiting webhook shows capture step timestamp and webhook pending")
    void awaitingConfirmationOrderShowsCapturedApiAndWaitingWebhook() {
        ShopOrder awaiting = new ShopOrder(
            "ORD-101", "PP-101", "AWAITING_CONFIRMATION", 6250L, "USD", "Alice",
            t0, t0.plusSeconds(15), t0.plusSeconds(2), t0.plusSeconds(14), t0.plusSeconds(15), "CAP-101", null
        );
        when(shopOrderService.findOrderById("ORD-101")).thenReturn(Optional.of(awaiting));
        when(shopOrderService.findPaymentByOrderId("ORD-101")).thenReturn(Optional.empty());
        when(eventQueryService.findEventsForOrder("ORD-101", "PP-101", "CAP-101")).thenReturn(List.of());
        when(caseService.findCasesForOrder("ORD-101", "PP-101")).thenReturn(List.of());

        OrderTraceDto trace = orderTraceService.getOrderTrace("ORD-101");

        assertThat(stepById(trace, "ORDER_CREATED").timestamp()).isEqualTo(t0);
        assertThat(stepById(trace, "PAYPAL_ORDER_CREATED").timestamp()).isEqualTo(t0.plusSeconds(2));
        assertThat(stepById(trace, "BUYER_APPROVED").timestamp()).isEqualTo(t0.plusSeconds(14));
        assertThat(stepById(trace, "CAPTURED_API").timestamp()).isEqualTo(t0.plusSeconds(15));
        assertThat(stepById(trace, "CAPTURED_API").externalRef()).isEqualTo("CAP-101");
        assertThat(stepById(trace, "WEBHOOK_RECEIVED").timestampLabel()).isEqualTo("not yet");
        assertThat(stepById(trace, "WEBHOOK_PROCESSED").timestampLabel()).isEqualTo("not yet");
        assertThat(stepById(trace, "ORDER_MARKED_PAID").timestampLabel()).isEqualTo("not yet");
    }

    @Test
    @DisplayName("3. Unverified webhook shows UNVERIFIED status and blocks processing")
    void unverifiedWebhookMarksReceivedUnverifiedAndBlocksProcessing() {
        ShopOrder awaiting = new ShopOrder(
            "ORD-102", "PP-102", "AWAITING_CONFIRMATION", 3850L, "USD", "Bob",
            t0, t0.plusSeconds(15), t0.plusSeconds(2), t0.plusSeconds(14), t0.plusSeconds(15), "CAP-102", null
        );
        OrderWebhookTraceDto unverifiedEvent = new OrderWebhookTraceDto(
            10L, "WH-BAD-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-102",
            false, "FAILED", 1, t0.plusSeconds(18), t0.plusSeconds(18),
            "PayPal signature verification failed", t0.plusSeconds(17)
        );

        when(shopOrderService.findOrderById("ORD-102")).thenReturn(Optional.of(awaiting));
        when(shopOrderService.findPaymentByOrderId("ORD-102")).thenReturn(Optional.empty());
        when(eventQueryService.findEventsForOrder("ORD-102", "PP-102", "CAP-102")).thenReturn(List.of(unverifiedEvent));
        when(caseService.findCasesForOrder("ORD-102", "PP-102")).thenReturn(List.of());

        OrderTraceDto trace = orderTraceService.getOrderTrace("ORD-102");

        OrderTraceStepDto received = stepById(trace, "WEBHOOK_RECEIVED");
        assertThat(received.completed()).isTrue();
        assertThat(received.status()).isEqualTo("UNVERIFIED");
        assertThat(received.timestamp()).isEqualTo(t0.plusSeconds(18));

        OrderTraceStepDto processed = stepById(trace, "WEBHOOK_PROCESSED");
        assertThat(processed.completed()).isFalse();
        assertThat(processed.status()).isEqualTo("UNVERIFIED");
        assertThat(processed.timestampLabel()).isEqualTo("not yet");
    }

    @Test
    @DisplayName("4. Verified failed webhook + opened case highlights failure reason and linked case")
    void failedVerifiedWebhookAndOpenedCase() {
        ShopOrder awaiting = new ShopOrder(
            "ORD-103", "PP-103", "AWAITING_CONFIRMATION", 8450L, "USD", "Charlie",
            t0, t0.plusSeconds(15), t0.plusSeconds(2), t0.plusSeconds(14), t0.plusSeconds(15), "CAP-103", null
        );
        OrderWebhookTraceDto failedVerified = new OrderWebhookTraceDto(
            11L, "WH-FAIL-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-103",
            true, "FAILED", 3, t0.plusSeconds(18), t0.plusSeconds(25),
            "Simulated handler failure (demo fault switch active)", t0.plusSeconds(17)
        );
        CaseDetailsDto openedCase = new CaseDetailsDto(
            "PR-0199", "PAID_NOT_MARKED", "Paid, not marked", "Open", "High",
            "PP-103", "ORD-103", "$84.50", 8450L, "USD", t0.plusSeconds(26), t0.plusSeconds(26), null,
            "PayPal capture completed, shop order remains AWAITING_CONFIRMATION"
        );

        when(shopOrderService.findOrderById("ORD-103")).thenReturn(Optional.of(awaiting));
        when(shopOrderService.findPaymentByOrderId("ORD-103")).thenReturn(Optional.empty());
        when(eventQueryService.findEventsForOrder("ORD-103", "PP-103", "CAP-103")).thenReturn(List.of(failedVerified));
        when(caseService.findCasesForOrder("ORD-103", "PP-103")).thenReturn(List.of(openedCase));

        OrderTraceDto trace = orderTraceService.getOrderTrace("ORD-103");

        assertThat(stepById(trace, "WEBHOOK_RECEIVED").status()).isEqualTo("VERIFIED");
        OrderTraceStepDto processed = stepById(trace, "WEBHOOK_PROCESSED");
        assertThat(processed.completed()).isFalse();
        assertThat(processed.status()).isEqualTo("FAILED");
        assertThat(processed.timestampLabel()).isEqualTo("not yet");
        assertThat(processed.detail()).contains("Simulated handler failure");

        OrderTraceStepDto caseStep = stepById(trace, "CASE_OPENED");
        assertThat(caseStep.completed()).isTrue();
        assertThat(caseStep.status()).isEqualTo("OPENED");
        assertThat(caseStep.externalRef()).isEqualTo("PR-0199");
        assertThat(caseStep.timestamp()).isEqualTo(t0.plusSeconds(26));
        assertThat(trace.linkedCaseId()).isEqualTo("PR-0199");
    }

    @Test
    @DisplayName("5. Complete PAID order has all 7 payment steps timestamped and no case opened")
    void completePaidOrderTrace() {
        ShopOrder paid = new ShopOrder(
            "ORD-104", "PP-104", "PAID", 2400L, "USD", "Dana",
            t0, t0.plusSeconds(22), t0.plusSeconds(2), t0.plusSeconds(14), t0.plusSeconds(15), "CAP-104", t0.plusSeconds(22)
        );
        OrderWebhookTraceDto approved = new OrderWebhookTraceDto(
            12L, "WH-APP-1", "CHECKOUT.ORDER.APPROVED", "PP-104",
            true, "PROCESSED", 1, t0.plusSeconds(14), t0.plusSeconds(14), null, t0.plusSeconds(13)
        );
        OrderWebhookTraceDto captureOk = new OrderWebhookTraceDto(
            13L, "WH-CAP-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-104",
            true, "PROCESSED", 1, t0.plusSeconds(18), t0.plusSeconds(22), null, t0.plusSeconds(17)
        );
        PaymentRecord payment = new PaymentRecord("CAP-104", "ORD-104", "CAP-104", 2400L, "USD", "COMPLETED", t0.plusSeconds(22));

        when(shopOrderService.findOrderById("ORD-104")).thenReturn(Optional.of(paid));
        when(shopOrderService.findPaymentByOrderId("ORD-104")).thenReturn(Optional.of(payment));
        when(eventQueryService.findEventsForOrder("ORD-104", "PP-104", "CAP-104")).thenReturn(List.of(approved, captureOk));
        when(caseService.findCasesForOrder("ORD-104", "PP-104")).thenReturn(List.of());

        OrderTraceDto trace = orderTraceService.getOrderTrace("ORD-104");

        assertThat(stepById(trace, "ORDER_CREATED").completed()).isTrue();
        assertThat(stepById(trace, "PAYPAL_ORDER_CREATED").completed()).isTrue();
        assertThat(stepById(trace, "BUYER_APPROVED").completed()).isTrue();
        assertThat(stepById(trace, "CAPTURED_API").completed()).isTrue();
        assertThat(stepById(trace, "WEBHOOK_RECEIVED").status()).isEqualTo("VERIFIED");
        assertThat(stepById(trace, "WEBHOOK_PROCESSED").completed()).isTrue();
        assertThat(stepById(trace, "WEBHOOK_PROCESSED").timestamp()).isEqualTo(t0.plusSeconds(22));
        assertThat(stepById(trace, "ORDER_MARKED_PAID").completed()).isTrue();
        assertThat(stepById(trace, "ORDER_MARKED_PAID").timestamp()).isEqualTo(t0.plusSeconds(22));
        assertThat(stepById(trace, "CASE_OPENED").completed()).isFalse();
        assertThat(stepById(trace, "CASE_OPENED").timestampLabel()).isEqualTo("not yet");
    }

    private static OrderTraceStepDto stepById(OrderTraceDto trace, String id) {
        return trace.steps().stream()
            .filter(s -> id.equals(s.id()))
            .findFirst()
            .orElseThrow();
    }
}
