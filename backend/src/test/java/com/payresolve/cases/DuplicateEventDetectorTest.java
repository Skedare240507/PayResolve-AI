package com.payresolve.cases;

import com.payresolve.ingest.WebhookEvent;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.ShopOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateEventDetectorTest {

    private final DuplicateEventDetector detector = new DuplicateEventDetector();

    static Stream<Arguments> provideScenarios() {
        Instant t0 = Instant.parse("2026-11-07T12:00:00Z");
        ShopOrder order = new ShopOrder("ORD-DUP", "PP-DUP", "PAID", 3200L, "USD", "Emma", t0, t0);

        WebhookEvent singleAttemptEvent = new WebhookEvent(
            1L, "EVT-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-1", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );

        WebhookEvent repeatedAttemptEvent = new WebhookEvent(
            2L, "EVT-2", "PAYMENT.CAPTURE.COMPLETED", "CAP-2", "{}", "{}", true, "PROCESSED", 2, t0, t0, null
        );

        WebhookEvent duplicateIdEventA = new WebhookEvent(
            3L, "EVT-SAME", "PAYMENT.CAPTURE.COMPLETED", "CAP-3", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );
        WebhookEvent duplicateIdEventB = new WebhookEvent(
            4L, "EVT-SAME", "PAYMENT.CAPTURE.COMPLETED", "CAP-3", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );

        WebhookEvent deliveredTwiceEvent = new WebhookEvent(
            5L, "EVT-DELIV-2", "PAYMENT.CAPTURE.COMPLETED", "CAP-DELIV-2", "{}", "{}", true, "PROCESSED", 1, 2, t0, t0, null
        );
        PaymentRecord singlePayment = new PaymentRecord("PAY-SINGLE", "ORD-DUP", "CAP-DELIV-2", 3200L, "USD", "COMPLETED", t0);

        PaymentRecord payA = new PaymentRecord("PAY-1", "ORD-DUP", "CAP-DUP", 3200L, "USD", "COMPLETED", t0);
        PaymentRecord payB = new PaymentRecord("PAY-2", "ORD-DUP", "CAP-DUP", 3200L, "USD", "COMPLETED", t0);

        return Stream.of(
            // POSITIVE: Attempts > 1
            Arguments.of("Positive: Event with attempts = 2",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(repeatedAttemptEvent)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.MEDIUM),

            // POSITIVE: Repeated event IDs
            Arguments.of("Positive: Same event ID received twice",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(duplicateIdEventA)
                    .addWebhookEvent(duplicateIdEventB)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.MEDIUM),

            // POSITIVE: Repeated capture payment records
            Arguments.of("Positive: Duplicate payment records for same capture",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addShopPayment(payA)
                    .addShopPayment(payB)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.MEDIUM),

            // POSITIVE: delivery_count > 1 where shop order was marked paid once
            Arguments.of("Positive: Event delivered twice and shop order marked paid once",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(deliveredTwiceEvent)
                    .addShopPayment(singlePayment)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.MEDIUM),

            // NEGATIVE: Normal single event with attempts = 1
            Arguments.of("Negative: Single event, 1 attempt",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(singleAttemptEvent)
                    .build(),
                0, null, null),

            // EDGE CASE: Empty snapshot
            Arguments.of("Edge case: Empty snapshot",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .build(),
                0, null, null),

            // EDGE CASE: Distinct events with attempts = 1
            Arguments.of("Edge case: Distinct events with attempts = 1",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(singleAttemptEvent)
                    .addWebhookEvent(new WebhookEvent(6L, "EVT-6", "CHECKOUT.ORDER.APPROVED", "ORD-DUP", "{}", "{}", true, "PROCESSED", 1, t0, t0, null))
                    .build(),
                0, null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideScenarios")
    @DisplayName("Table-driven tests for DUPLICATE_EVENT detector")
    void shouldEvaluateScenarios(String name, DetectionSnapshot snapshot, int expectedCount, CaseType expectedType, CaseSeverity expectedSeverity) {
        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(expectedCount);

        if (expectedCount > 0) {
            DetectionFinding finding = findings.getFirst();
            assertThat(finding.type()).isEqualTo(expectedType);
            assertThat(finding.severity()).isEqualTo(expectedSeverity);
            assertThat(finding.summary()).contains("Duplicate");
        }
    }
}
