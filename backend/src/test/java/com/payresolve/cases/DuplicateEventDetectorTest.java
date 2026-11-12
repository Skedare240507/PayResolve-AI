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

        // A processing retry (attempts = 2, delivery_count = 1) is NOT a duplicate delivery
        WebhookEvent retriedEvent = new WebhookEvent(
            2L, "EVT-2", "PAYMENT.CAPTURE.COMPLETED", "CAP-2", "{}", "{}", true, "PROCESSED", 2, 1, t0, t0, null
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

        WebhookEvent payloadMismatchEvent = new WebhookEvent(
            9L, "EVT-MISMATCH", "PAYMENT.CAPTURE.COMPLETED", "CAP-MM", "{}", "{}", true, "PROCESSED",
            1, t0, t0, null, 2, t0, t0, "ORD-DUP", "PP-DUP", null, true
        );

        WebhookEvent distinctCapture1 = new WebhookEvent(
            10L, "EVT-CAP-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-SAME", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );
        WebhookEvent distinctCapture2 = new WebhookEvent(
            11L, "EVT-CAP-2", "PAYMENT.CAPTURE.COMPLETED", "CAP-SAME", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );

        WebhookEvent distinctApproved1 = new WebhookEvent(
            12L, "EVT-APP-1", "CHECKOUT.ORDER.APPROVED", "PP-DUP", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );
        WebhookEvent distinctApproved2 = new WebhookEvent(
            13L, "EVT-APP-2", "CHECKOUT.ORDER.APPROVED", "PP-DUP", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );

        PaymentRecord payA = new PaymentRecord("PAY-1", "ORD-DUP", "CAP-DUP", 3200L, "USD", "COMPLETED", t0);
        PaymentRecord payB = new PaymentRecord("PAY-2", "ORD-DUP", "CAP-DUP", 3200L, "USD", "COMPLETED", t0);

        return Stream.of(
            // NEGATIVE: Retried single delivery creates ZERO cases (check 2 removed)
            Arguments.of("Negative: Retried event (attempts = 2, delivery_count = 1)",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(retriedEvent)
                    .build(),
                0, null, null, null),

            // NEGATIVE: delivery_count > 1 where business effect ran once (absorbed duplicate) creates ZERO cases
            Arguments.of("Negative: Verified delivery twice with single payment effect (absorbed)",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(deliveredTwiceEvent)
                    .addShopPayment(singlePayment)
                    .build(),
                0, null, null, null),

            // POSITIVE (HIGH, OPEN): Repeated payment records for same capture
            Arguments.of("Positive high: Duplicate payment records for same capture",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addShopPayment(payA)
                    .addShopPayment(payB)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.HIGH, CaseStatus.OPEN),

            // POSITIVE (HIGH, OPEN): Same event ID carried conflicting payloads
            Arguments.of("Positive high: Same event ID carried conflicting payloads",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(payloadMismatchEvent)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.HIGH, CaseStatus.OPEN),

            // POSITIVE (HIGH, OPEN): Repeated event IDs
            Arguments.of("Positive high: Same event ID received twice in event log",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(duplicateIdEventA)
                    .addWebhookEvent(duplicateIdEventB)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.HIGH, CaseStatus.OPEN),

            // POSITIVE (HIGH, OPEN): Multiple PAYMENT.CAPTURE.COMPLETED events for same capture
            Arguments.of("Positive high: Two distinct PAYMENT.CAPTURE.COMPLETED events for same resource",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(distinctCapture1)
                    .addWebhookEvent(distinctCapture2)
                    .build(),
                1, CaseType.DUPLICATE_EVENT, CaseSeverity.HIGH, CaseStatus.OPEN),

            // NEGATIVE: Multiple CHECKOUT.ORDER.APPROVED events ignored by check 4 (only capture/refund)
            Arguments.of("Negative: Two CHECKOUT.ORDER.APPROVED events for same order do not trigger check 4",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(distinctApproved1)
                    .addWebhookEvent(distinctApproved2)
                    .build(),
                0, null, null, null),

            // NEGATIVE: Normal single event
            Arguments.of("Negative: Single event, 1 attempt",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addWebhookEvent(singleAttemptEvent)
                    .build(),
                0, null, null, null),

            // EDGE CASE: Empty snapshot
            Arguments.of("Edge case: Empty snapshot",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .build(),
                0, null, null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideScenarios")
    @DisplayName("Table-driven tests for DUPLICATE_EVENT detector")
    void shouldEvaluateScenarios(
        String name,
        DetectionSnapshot snapshot,
        int expectedCount,
        CaseType expectedType,
        CaseSeverity expectedSeverity,
        CaseStatus expectedStatus
    ) {
        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(expectedCount);

        if (expectedCount > 0) {
            DetectionFinding finding = findings.getFirst();
            assertThat(finding.type()).isEqualTo(expectedType);
            assertThat(finding.severity()).isEqualTo(expectedSeverity);
            assertThat(finding.initialStatus()).isEqualTo(expectedStatus);
            if (expectedSeverity == CaseSeverity.LOW && expectedStatus == CaseStatus.RESOLVED) {
                assertThat(finding.summary()).isEqualTo(DuplicateEventDetector.INFORMATIONAL_SUMMARY);
            }
        }
    }
}
