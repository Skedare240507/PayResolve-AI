package com.payresolve.cases;

import com.payresolve.ingest.WebhookEvent;
import com.payresolve.paypal.model.Amount;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.shop.ShopOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OrphanCaptureDetectorTest {

    private final OrphanCaptureDetector detector = new OrphanCaptureDetector();

    static Stream<Arguments> provideScenarios() {
        Instant t0 = Instant.parse("2026-11-07T12:00:00Z");
        ShopOrder order = new ShopOrder("ORD-MATCHED", "PP-MATCHED", "PAID", 12000L, "USD", "Nina", t0, t0);

        CaptureSummary completedCapture = new CaptureSummary(
            "CAP-ORPHAN-01", "COMPLETED", new Amount("USD", "120.00"), true, null, t0.toString(), t0.toString()
        );
        CaptureSummary pendingCapture = new CaptureSummary(
            "CAP-PENDING-02", "PENDING", new Amount("USD", "120.00"), false, null, t0.toString(), t0.toString()
        );

        WebhookEvent completedCaptureEvent = new WebhookEvent(
            1L, "EVT-CAP-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-ORPHAN-WH", "{}", "{}", true, "PROCESSED", 1, t0, t0, null
        );

        return Stream.of(
            // POSITIVE: Completed capture with null shop order
            Arguments.of("Positive: Completed capture with no shop order",
                DetectionSnapshot.builder()
                    .targetId("CAP-ORPHAN-01")
                    .shopOrder(null)
                    .addPaypalCapture(completedCapture)
                    .build(),
                1, CaseType.ORPHAN_CAPTURE, CaseSeverity.MEDIUM, "Orphan capture"),

            // POSITIVE: Verified webhook capture event with no shop order
            Arguments.of("Positive: Webhook capture event with no shop order",
                DetectionSnapshot.builder()
                    .targetId("CAP-ORPHAN-WH")
                    .shopOrder(null)
                    .addWebhookEvent(completedCaptureEvent)
                    .build(),
                1, CaseType.ORPHAN_CAPTURE, CaseSeverity.MEDIUM, "Orphan capture"),

            // NEGATIVE: Capture has a matching shop order
            Arguments.of("Negative: Capture matches existing shop order",
                DetectionSnapshot.builder()
                    .shopOrder(order)
                    .addPaypalCapture(completedCapture)
                    .build(),
                0, null, null, null),

            // EDGE CASE: Capture is PENDING with null shop order
            Arguments.of("Edge case: Pending capture with null shop order",
                DetectionSnapshot.builder()
                    .shopOrder(null)
                    .addPaypalCapture(pendingCapture)
                    .build(),
                0, null, null, null),

            // EDGE CASE: Empty snapshot
            Arguments.of("Edge case: Empty snapshot",
                DetectionSnapshot.builder()
                    .shopOrder(null)
                    .build(),
                0, null, null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideScenarios")
    @DisplayName("Table-driven tests for ORPHAN_CAPTURE detector")
    void shouldEvaluateScenarios(String name, DetectionSnapshot snapshot, int expectedCount, CaseType expectedType, CaseSeverity expectedSeverity, String expectedSummarySub) {
        List<DetectionFinding> findings = detector.detect(snapshot);
        assertThat(findings).hasSize(expectedCount);

        if (expectedCount > 0) {
            DetectionFinding finding = findings.getFirst();
            assertThat(finding.type()).isEqualTo(expectedType);
            assertThat(finding.severity()).isEqualTo(expectedSeverity);
            assertThat(finding.summary()).contains(expectedSummarySub);
            assertThat(finding.shopOrderId()).isNull();
        }
    }
}
