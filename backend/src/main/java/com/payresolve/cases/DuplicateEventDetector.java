package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.ShopOrder;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Detector for DUPLICATE_EVENT (P0):
 * - Processing retries (attempts > 1) and absorbed duplicate deliveries (delivery_count > 1 with
 *   identical payloads and a single business effect) create NO case.
 * - Creates an OPEN, HIGH severity DUPLICATE_EVENT case ONLY when:
 *   1. The business effect ran more than once (duplicate payment records for the same capture id), OR
 *   2. Two deliveries of the same event id carried conflicting payloads (payload_mismatch = true), OR
 *   3. Distinct event ids execute multiple capture/refund notifications for the same resource.
 */
@Component
public class DuplicateEventDetector implements Detector {

    public static final String INFORMATIONAL_SUMMARY =
        "Duplicate delivery absorbed by idempotency; business effect ran once";

    private static final Set<String> STRICT_SINGLE_EFFECT_EVENT_TYPES = Set.of(
        "PAYMENT.CAPTURE.COMPLETED",
        "PAYMENT.CAPTURE.REFUNDED"
    );

    @Override
    public CaseType supportedType() {
        return CaseType.DUPLICATE_EVENT;
    }

    @Override
    public List<DetectionFinding> detect(DetectionSnapshot snapshot) {
        ShopOrder shopOrder = snapshot.shopOrder();
        String paypalOrderId = shopOrder != null && shopOrder.paypalOrderId() != null
            ? shopOrder.paypalOrderId()
            : null;
        String shopOrderId = shopOrder != null ? shopOrder.id() : null;
        Money orderAmount = shopOrder != null ? shopOrder.total() : null;

        List<DetectionFinding> findings = new ArrayList<>();

        // Check 1 (HIGH, OPEN): Multiple payment records with the same PayPal capture ID (business effect ran > 1 time)
        Set<String> seenCaptureIds = new HashSet<>();
        for (PaymentRecord payment : snapshot.shopPayments()) {
            if (payment.paypalCaptureId() != null && !seenCaptureIds.add(payment.paypalCaptureId())) {
                String summary = String.format(
                    "Duplicate payment capture records detected for PayPal capture ID %s on order %s.",
                    payment.paypalCaptureId(), shopOrderId
                );
                findings.add(DetectionFinding.of(
                    CaseType.DUPLICATE_EVENT,
                    CaseSeverity.HIGH,
                    paypalOrderId != null ? paypalOrderId : payment.paypalCaptureId(),
                    shopOrderId,
                    orderAmount,
                    summary
                ));
                return findings;
            }
        }

        Set<String> seenEventIds = new HashSet<>();
        Map<String, Integer> resourceTypeCounts = new HashMap<>();

        for (WebhookEvent event : snapshot.webhookEvents()) {
            if (paypalOrderId == null && event.paypalOrderId() != null) {
                paypalOrderId = event.paypalOrderId();
            } else if (paypalOrderId == null && event.resourceId() != null) {
                paypalOrderId = event.resourceId();
            }

            // Check 2 (HIGH, OPEN): Two deliveries of the same event id carried different payloads
            if (event.payloadMismatch()) {
                String summary = String.format(
                    "Duplicate webhook deliveries for event %s carried conflicting payloads.",
                    event.paypalEventId()
                );
                findings.add(DetectionFinding.of(
                    CaseType.DUPLICATE_EVENT,
                    CaseSeverity.HIGH,
                    paypalOrderId != null ? paypalOrderId : event.paypalEventId(),
                    shopOrderId,
                    orderAmount,
                    summary
                ));
                return findings;
            }

            // Absorbed duplicate deliveries (delivery_count > 1 with identical payload and single effect)
            // create NO case; they are tracked on the Events page and counted in "Duplicates blocked".

            // Check 4 (HIGH, OPEN): Repeated verified event IDs present as separate rows
            if (event.paypalEventId() != null && !seenEventIds.add(event.paypalEventId())) {
                String summary = String.format(
                    "Duplicate webhook event ID %s received multiple times in event log.",
                    event.paypalEventId()
                );
                findings.add(DetectionFinding.of(
                    CaseType.DUPLICATE_EVENT,
                    CaseSeverity.HIGH,
                    paypalOrderId != null ? paypalOrderId : event.paypalEventId(),
                    shopOrderId,
                    orderAmount,
                    summary
                ));
                return findings;
            }

            // Check 5: Distinct event IDs for the same resource & event type (only PAYMENT.CAPTURE.COMPLETED / REFUNDED)
            if (event.resourceId() != null && event.eventType() != null) {
                String normalizedType = event.eventType().trim().toUpperCase(Locale.ROOT);
                if (STRICT_SINGLE_EFFECT_EVENT_TYPES.contains(normalizedType)) {
                    String key = normalizedType + "::" + event.resourceId();
                    int count = resourceTypeCounts.merge(key, 1, Integer::sum);
                    if (count > 1) {
                        String summary = String.format(
                            "Duplicate %s events with distinct IDs delivered for capture resource %s.",
                            normalizedType, event.resourceId()
                        );
                        findings.add(DetectionFinding.of(
                            CaseType.DUPLICATE_EVENT,
                            CaseSeverity.HIGH,
                            paypalOrderId != null ? paypalOrderId : event.resourceId(),
                            shopOrderId,
                            orderAmount,
                            summary
                        ));
                        return findings;
                    }
                }
            }
        }

        return findings;
    }
}
