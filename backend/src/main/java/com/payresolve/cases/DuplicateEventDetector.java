package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.ShopOrder;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Detector for DUPLICATE_EVENT (P0):
 * Discrepancy occurs when the same webhook event is delivered more than once (delivery_count > 1),
 * while requiring that the business effect ran only once (idempotent deduplication).
 */
@Component
public class DuplicateEventDetector implements Detector {

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
        Set<String> seenEventIds = new HashSet<>();
        Map<String, Integer> resourceTypeCounts = new HashMap<>();

        // Check webhook events
        for (WebhookEvent event : snapshot.webhookEvents()) {
            if (paypalOrderId == null && event.paypalOrderId() != null) {
                paypalOrderId = event.paypalOrderId();
            } else if (paypalOrderId == null && event.resourceId() != null) {
                paypalOrderId = event.resourceId();
            }

            // Check 1: delivery_count > 1 (business effect must have run only once)
            if (event.deliveryCount() > 1) {
                boolean businessEffectOnlyOnce = snapshot.shopPayments().size() <= 1;
                if (businessEffectOnlyOnce) {
                    String summary = String.format(
                        "Duplicate webhook delivery detected for event %s (delivered %d times); business effect executed once idempotently.",
                        event.paypalEventId(), event.deliveryCount()
                    );
                    findings.add(DetectionFinding.of(
                        CaseType.DUPLICATE_EVENT,
                        CaseSeverity.MEDIUM,
                        paypalOrderId != null ? paypalOrderId : event.paypalEventId(),
                        shopOrderId,
                        orderAmount,
                        summary
                    ));
                    return findings;
                }
            }

            // Check 2: Attempt count > 1
            if (event.attempts() > 1) {
                String summary = String.format(
                    "Duplicate delivery or repeated attempt detected for webhook event %s (attempts: %d).",
                    event.paypalEventId(), event.attempts()
                );
                findings.add(DetectionFinding.of(
                    CaseType.DUPLICATE_EVENT,
                    CaseSeverity.MEDIUM,
                    paypalOrderId != null ? paypalOrderId : event.paypalEventId(),
                    shopOrderId,
                    orderAmount,
                    summary
                ));
                return findings;
            }

            // Check 3: Repeated event IDs in snapshot
            if (event.paypalEventId() != null && !seenEventIds.add(event.paypalEventId())) {
                String summary = String.format(
                    "Duplicate webhook event ID %s received multiple times in event log.",
                    event.paypalEventId()
                );
                findings.add(DetectionFinding.of(
                    CaseType.DUPLICATE_EVENT,
                    CaseSeverity.MEDIUM,
                    paypalOrderId != null ? paypalOrderId : event.paypalEventId(),
                    shopOrderId,
                    orderAmount,
                    summary
                ));
                return findings;
            }

            // Check 4: Repeated events for the same resource and event type
            if (event.resourceId() != null && event.eventType() != null) {
                String key = event.eventType() + "::" + event.resourceId();
                int count = resourceTypeCounts.merge(key, 1, Integer::sum);
                if (count > 1) {
                    String summary = String.format(
                        "Multiple %s events delivered for resource %s.",
                        event.eventType(), event.resourceId()
                    );
                    findings.add(DetectionFinding.of(
                        CaseType.DUPLICATE_EVENT,
                        CaseSeverity.MEDIUM,
                        paypalOrderId != null ? paypalOrderId : event.resourceId(),
                        shopOrderId,
                        orderAmount,
                        summary
                    ));
                    return findings;
                }
            }
        }

        // Check 5: Multiple payment records with the same PayPal capture ID
        Set<String> seenCaptureIds = new HashSet<>();
        for (PaymentRecord payment : snapshot.shopPayments()) {
            if (payment.paypalCaptureId() != null && !seenCaptureIds.add(payment.paypalCaptureId())) {
                String summary = String.format(
                    "Duplicate payment capture records detected for PayPal capture ID %s on order %s.",
                    payment.paypalCaptureId(), shopOrderId
                );
                findings.add(DetectionFinding.of(
                    CaseType.DUPLICATE_EVENT,
                    CaseSeverity.MEDIUM,
                    paypalOrderId != null ? paypalOrderId : payment.paypalCaptureId(),
                    shopOrderId,
                    orderAmount,
                    summary
                ));
                return findings;
            }
        }

        return findings;
    }
}
