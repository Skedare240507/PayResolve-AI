package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.paypal.model.Amount;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.shop.PaymentRecord;
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

class AmountMismatchDetectorTest {

    private final AmountMismatchDetector detector = new AmountMismatchDetector();
    private final Instant baseTime = Instant.parse("2026-11-07T12:00:00Z");

    static Stream<Arguments> provideScenarios() {
        Instant t0 = Instant.parse("2026-11-07T12:00:00Z");
        ShopOrder orderStandard = new ShopOrder("ORD-1", "PP-1", "AWAITING_CONFIRMATION", 7950L, "USD", "Alice", t0, t0);

        return Stream.of(
            // POSITIVE: Amount mismatch ($84.50 captured vs $79.50 order)
            Arguments.of("Positive: Minor units mismatch ($84.50 captured vs $79.50 order)",
                DetectionSnapshot.builder()
                    .shopOrder(orderStandard)
                    .addShopPayment(new PaymentRecord("PAY-1", "ORD-1", "CAP-1", 8450L, "USD", "COMPLETED", t0))
                    .build(),
                1, CaseType.AMOUNT_MISMATCH, CaseSeverity.HIGH, "discrepancy"),

            // POSITIVE: Currency mismatch invariant ($79.50 EUR vs $79.50 USD)
            Arguments.of("Positive: Currency mismatch (EUR vs USD)",
                DetectionSnapshot.builder()
                    .shopOrder(orderStandard)
                    .addShopPayment(new PaymentRecord("PAY-2", "ORD-1", "CAP-2", 7950L, "EUR", "COMPLETED", t0))
                    .build(),
                1, CaseType.AMOUNT_MISMATCH, CaseSeverity.HIGH, "Currency mismatch"),

            // NEGATIVE: Exact match in minor units and currency
            Arguments.of("Negative: Exact amount and currency match",
                DetectionSnapshot.builder()
                    .shopOrder(orderStandard)
                    .addShopPayment(new PaymentRecord("PAY-3", "ORD-1", "CAP-3", 7950L, "USD", "COMPLETED", t0))
                    .build(),
                0, null, null, null),

            // EDGE CASE: Shop order is null
            Arguments.of("Edge case: Null shop order",
                DetectionSnapshot.builder()
                    .shopOrder(null)
                    .addShopPayment(new PaymentRecord("PAY-4", "ORD-1", "CAP-4", 7950L, "USD", "COMPLETED", t0))
                    .build(),
                0, null, null, null),

            // EDGE CASE: No payments present
            Arguments.of("Edge case: No payments recorded",
                DetectionSnapshot.builder()
                    .shopOrder(orderStandard)
                    .build(),
                0, null, null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideScenarios")
    @DisplayName("Table-driven tests for AMOUNT_MISMATCH detector")
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
    @DisplayName("Edge case: evaluate amount mismatch from PayPal capture summary")
    void shouldEvaluateFromCaptureSummary() {
        ShopOrder order = new ShopOrder("ORD-CS", "PP-CS", "AWAITING_CONFIRMATION", 2400L, "USD", "Bob", baseTime, baseTime);
        CaptureSummary capture = new CaptureSummary("CAP-CS", "COMPLETED", new Amount("USD", "34.00"), true, null, null, null);

        DetectionSnapshot snapshot = DetectionSnapshot.builder()
            .shopOrder(order)
            .addPaypalCapture(capture)
            .build();

        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().type()).isEqualTo(CaseType.AMOUNT_MISMATCH);
        assertThat(findings.getFirst().summary()).contains("34.00 USD");
    }
}
