package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.ShopOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PaidNotMarkedDetectorTest {

    private final PaidNotMarkedDetector detector = new PaidNotMarkedDetector();
    private final Instant baseTime = Instant.parse("2026-11-07T12:00:00Z");

    static Stream<Arguments> provideScenarios() {
        Instant t0 = Instant.parse("2026-11-07T12:00:00Z");
        ShopOrder orderAwaiting = new ShopOrder("ORD-1", "PP-1", "AWAITING_CONFIRMATION", 2400L, "USD", "Alice", t0, t0);
        ShopOrder orderPending = new ShopOrder("ORD-2", "PP-2", "PENDING", 3850L, "USD", "Bob", t0, t0);
        ShopOrder orderPaid = new ShopOrder("ORD-3", "PP-3", "PAID", 2200L, "USD", "Charlie", t0, t0);
        ShopOrder orderRefunded = new ShopOrder("ORD-4", "PP-4", "REFUNDED", 2400L, "USD", "Dave", t0, t0);

        PaymentRecord paymentCompleted = new PaymentRecord("PAY-1", "ORD-1", "CAP-1", 2400L, "USD", "COMPLETED", t0);
        PaymentRecord paymentPending = new PaymentRecord("PAY-2", "ORD-2", "CAP-2", 3850L, "USD", "PENDING", t0);

        return Stream.of(
            // POSITIVE: capture completed, shop order AWAITING_CONFIRMATION, grace expired
            Arguments.of("Positive: Awaiting confirmation with completed payment",
                DetectionSnapshot.builder()
                    .shopOrder(orderAwaiting)
                    .addShopPayment(paymentCompleted)
                    .evaluatedAt(t0.plusSeconds(300))
                    .gracePeriod(Duration.ofMinutes(2))
                    .build(),
                1, CaseType.PAID_NOT_MARKED, CaseSeverity.HIGH),

            // POSITIVE: Pending order with completed payment, grace expired
            Arguments.of("Positive: Pending order with completed payment",
                DetectionSnapshot.builder()
                    .shopOrder(orderPending)
                    .addShopPayment(new PaymentRecord("PAY-2B", "ORD-2", "CAP-2B", 3850L, "USD", "COMPLETED", t0))
                    .evaluatedAt(t0.plusSeconds(300))
                    .gracePeriod(Duration.ZERO)
                    .build(),
                1, CaseType.PAID_NOT_MARKED, CaseSeverity.HIGH),

            // NEGATIVE: Already PAID
            Arguments.of("Negative: Order is already PAID",
                DetectionSnapshot.builder()
                    .shopOrder(orderPaid)
                    .addShopPayment(new PaymentRecord("PAY-3", "ORD-3", "CAP-3", 2200L, "USD", "COMPLETED", t0))
                    .evaluatedAt(t0.plusSeconds(300))
                    .build(),
                0, null, null),

            // NEGATIVE: Payment is still PENDING, no completed capture
            Arguments.of("Negative: Payment is still PENDING",
                DetectionSnapshot.builder()
                    .shopOrder(orderPending)
                    .addShopPayment(paymentPending)
                    .evaluatedAt(t0.plusSeconds(300))
                    .build(),
                0, null, null),

            // EDGE CASE: Within grace period
            Arguments.of("Edge case: Within grace period (1 min elapsed of 5 min grace)",
                DetectionSnapshot.builder()
                    .shopOrder(orderAwaiting)
                    .addShopPayment(paymentCompleted)
                    .evaluatedAt(t0.plusSeconds(60))
                    .gracePeriod(Duration.ofMinutes(5))
                    .build(),
                0, null, null),

            // EDGE CASE: Shop order is already REFUNDED
            Arguments.of("Edge case: Order is REFUNDED",
                DetectionSnapshot.builder()
                    .shopOrder(orderRefunded)
                    .addShopPayment(paymentCompleted)
                    .evaluatedAt(t0.plusSeconds(300))
                    .build(),
                0, null, null),

            // EDGE CASE: Null shop order
            Arguments.of("Edge case: Shop order is null",
                DetectionSnapshot.builder()
                    .shopOrder(null)
                    .addShopPayment(paymentCompleted)
                    .evaluatedAt(t0.plusSeconds(300))
                    .build(),
                0, null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideScenarios")
    @DisplayName("Table-driven tests for PAID_NOT_MARKED detector")
    void shouldEvaluateScenarios(String scenarioName, DetectionSnapshot snapshot, int expectedCount, CaseType expectedType, CaseSeverity expectedSeverity) {
        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(expectedCount);

        if (expectedCount > 0) {
            DetectionFinding finding = findings.getFirst();
            assertThat(finding.type()).isEqualTo(expectedType);
            assertThat(finding.severity()).isEqualTo(expectedSeverity);
            assertThat(finding.summary()).contains("remains");
        }
    }

    @Test
    @DisplayName("Edge case: should detect when capture is confirmed by verified webhook event")
    void shouldDetectFromVerifiedWebhookEvent() {
        ShopOrder order = new ShopOrder("ORD-WH", "PP-WH", "AWAITING_CONFIRMATION", 5000L, "USD", "Eve", baseTime, baseTime);
        WebhookEvent event = new WebhookEvent(
            1L, "WH-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-WH", "{}", "{}", true, "PROCESSED", 1, baseTime, baseTime, null
        );

        DetectionSnapshot snapshot = DetectionSnapshot.builder()
            .shopOrder(order)
            .addWebhookEvent(event)
            .evaluatedAt(baseTime.plusSeconds(60))
            .gracePeriod(Duration.ZERO)
            .build();

        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().type()).isEqualTo(CaseType.PAID_NOT_MARKED);
        assertThat(findings.getFirst().shopOrderId()).isEqualTo("ORD-WH");
    }
}
