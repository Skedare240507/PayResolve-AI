package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.shop.ShopOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detector for ORPHAN_CAPTURE (P1):
 * Discrepancy occurs when a PayPal capture completes successfully, but
 * no matching merchant shop order exists in the system (missing or unmapped custom_id).
 * Stores 'capture:' + captureId in paypal_order_id column for deduplication.
 */
@Component
public class OrphanCaptureDetector implements Detector {

    @Override
    public CaseType supportedType() {
        return CaseType.ORPHAN_CAPTURE;
    }

    @Override
    public List<DetectionFinding> detect(DetectionSnapshot snapshot) {
        ShopOrder shopOrder = snapshot.shopOrder();

        // If a shop order exists, this is not an orphan capture
        if (shopOrder != null) {
            return List.of();
        }

        List<DetectionFinding> findings = new ArrayList<>();

        // Check PayPal captures in snapshot
        for (CaptureSummary capture : snapshot.paypalCaptures()) {
            if ("COMPLETED".equalsIgnoreCase(capture.status())) {
                Money captureAmount = capture.amount() != null
                    ? Money.parse(capture.amount().value(), capture.amount().currencyCode())
                    : null;
                String captureId = capture.id() != null ? capture.id() : "CAP-ORPHAN";
                String dedupeKey = "capture:" + captureId;

                String summary = String.format(
                    "Orphan capture: PayPal capture %s (%s) has no matching merchant shop order.",
                    captureId, captureAmount != null ? captureAmount.toString() : "unknown amount"
                );

                findings.add(DetectionFinding.of(
                    CaseType.ORPHAN_CAPTURE,
                    CaseSeverity.MEDIUM,
                    dedupeKey,
                    null,
                    captureAmount,
                    captureAmount,
                    summary
                ));
                return findings;
            }
        }

        // Check webhook events for completed captures with no shop order
        for (WebhookEvent event : snapshot.webhookEvents()) {
            if (event.verified() && "PAYMENT.CAPTURE.COMPLETED".equalsIgnoreCase(event.eventType())) {
                String captureId = event.resourceId() != null ? event.resourceId() : "CAP-ORPHAN";
                String dedupeKey = "capture:" + captureId;

                String summary = String.format(
                    "Orphan capture: Webhook confirms capture %s completed, but no merchant shop order exists.",
                    captureId
                );

                findings.add(DetectionFinding.of(
                    CaseType.ORPHAN_CAPTURE,
                    CaseSeverity.MEDIUM,
                    dedupeKey,
                    null,
                    null,
                    null,
                    summary
                ));
                return findings;
            }
        }

        return findings;
    }
}
