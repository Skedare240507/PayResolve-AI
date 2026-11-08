package com.payresolve.cases;

import com.payresolve.ingest.WebhookEvent;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShopOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RefundMismatchDetectorTest {

    private final RefundMismatchDetector detector = new RefundMismatchDetector();
    private final Instant baseTime = Instant.parse("2026-11-07T12:00:00Z");

    static Stream<Arguments> provideScenarios() {
        Instant t0 = Instant.parse("2026-11-07T12:00:00Z");
        ShopOrder order = new ShopOrder("ORD-REF", "PP-REF", "PAID", 1800L, "USD", "Lucas", t0, t0);

        RefundRecord shopRefund1800 = new RefundRecord("REF-1", "PAY-1", "PP-REF-1", 1800L, "USD", "COMPLETED", "SHOP", t0);
        RefundRecord paypalRefund1800 = new RefundRecord("REF-PP", "PAY-1", "PP-REF-1", 1800L, "USD", "COMPLETED", "PAYPAL", t0);
        RefundRecord paypalRefund2000 = new RefundRecord("REF-PP", "PAY-1", "PP-REF-1", 2000L, "USD", "COMPLETED", "PAYPAL", t0);

        return Stream.of(
            // POSITIVE: PayPal processed refund, but shop has 0 refunds
            Arguments.of("Positive: PayPal refund present, missing in shop",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addPaypalRefund(paypalRefund1800)
                    .build(),
                1, CaseType.REFUND_MISMATCH, CaseSeverity.MEDIUM, "PayPal processed refund"),

            // POSITIVE: Shop recorded refund, missing in PayPal
            Arguments.of("Positive: Shop refund recorded, missing in PayPal",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addShopRefund(shopRefund1800)
                    .build(),
                1, CaseType.REFUND_MISMATCH, CaseSeverity.MEDIUM, "Merchant shop recorded refund"),

            // POSITIVE: Refund amount mismatch (1800 shop vs 2000 PayPal)
            Arguments.of("Positive: Refund amount mismatch",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addShopRefund(shopRefund1800)
                    .addPaypalRefund(paypalRefund2000)
                    .build(),
                1, CaseType.REFUND_MISMATCH, CaseSeverity.MEDIUM, "Refund amount mismatch"),

            // NEGATIVE: Matching refunds
            Arguments.of("Negative: Exactly matching refunds",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addShopRefund(shopRefund1800)
                    .addPaypalRefund(paypalRefund1800)
                    .build(),
                0, null, null, null),

            // EDGE CASE: Neither has refunds
            Arguments.of("Edge case: Neither has refunds",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .build(),
                0, null, null, null),

            // EDGE CASE: Shop order is null
            Arguments.of("Edge case: Shop order is null",
                DetectionSnapshot.builder()
                    .shopOrder(null)
                    .addShopRefund(shopRefund1800)
                    .build(),
                0, null, null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideScenarios")
    @DisplayName("Table-driven tests for REFUND_MISMATCH detector")
    void shouldEvaluateScenarios(String name, DetectionSnapshot snapshot, int expectedCount, CaseType expectedType, CaseSeverity expectedSeverity, String expectedSummarySub) {
        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(expectedCount);

        if (expectedCount > 0) {
            DetectionFinding finding = findings.getFirst();
            assertThat(finding.type()).isEqualTo(expectedType);
            assertThat(finding.severity()).isEqualTo(expectedSeverity);
            assertThat(finding.summary()).contains(expectedSummarySub);
        }
    }

    @Test
    @DisplayName("Edge case: detected when PayPal refund is indicated via webhook event")
    void shouldDetectFromRefundWebhookEvent() {
        ShopOrder order = new ShopOrder("ORD-WH-REF", "PP-WH-REF", "PAID", 2500L, "USD", "Mia", baseTime, baseTime);
        WebhookEvent event = new WebhookEvent(
            1L, "EVT-REF", "PAYMENT.CAPTURE.REFUNDED", "REF-PP-99", "{}", "{}", true, "PROCESSED", 1, baseTime, baseTime, null
        );

        DetectionSnapshot snapshot = DetectionSnapshot.builder()
            .shopOrder(order)
            .addWebhookEvent(event)
            .build();

        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().type()).isEqualTo(CaseType.REFUND_MISMATCH);
        assertThat(findings.getFirst().summary()).contains("PayPal processed refund");
    }
}
