package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.ShopOrder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Detector for PAID_NOT_MARKED (P0):
 * Discrepancy occurs when PayPal capture is COMPLETED, but the merchant shop order
 * has not transitioned to PAID after the allowable grace period.
 */
@Component
public class PaidNotMarkedDetector implements Detector {

    @Override
    public CaseType supportedType() {
        return CaseType.PAID_NOT_MARKED;
    }

    @Override
    public List<DetectionFinding> detect(DetectionSnapshot snapshot) {
        ShopOrder shopOrder = snapshot.shopOrder();
        if (shopOrder == null) {
            return List.of();
        }

        // If order is already PAID, no discrepancy
        if (shopOrder.isPaid()) {
            return List.of();
        }

        // If order was explicitly refunded or cancelled, not an unconfirmed payment exception
        if ("REFUNDED".equalsIgnoreCase(shopOrder.status()) || "CANCELLED".equalsIgnoreCase(shopOrder.status())) {
            return List.of();
        }

        // Determine if a completed capture exists
        boolean hasCompletedCapture = false;
        Instant captureTime = null;
        Money capturedMoney = null;

        for (PaymentRecord payment : snapshot.shopPayments()) {
            if ("COMPLETED".equalsIgnoreCase(payment.status())) {
                hasCompletedCapture = true;
                captureTime = payment.capturedAt();
                capturedMoney = payment.amount();
                break;
            }
        }

        if (!hasCompletedCapture) {
            for (CaptureSummary capture : snapshot.paypalCaptures()) {
                if ("COMPLETED".equalsIgnoreCase(capture.status())) {
                    hasCompletedCapture = true;
                    if (capture.amount() != null) {
                        capturedMoney = Money.parse(capture.amount().value(), capture.amount().currencyCode());
                    }
                    if (capture.createTime() != null) {
                        try {
                            captureTime = Instant.parse(capture.createTime());
                        } catch (Exception ignored) {
                        }
                    }
                    break;
                }
            }
        }

        if (!hasCompletedCapture) {
            for (WebhookEvent event : snapshot.webhookEvents()) {
                if (event.verified() && "PAYMENT.CAPTURE.COMPLETED".equalsIgnoreCase(event.eventType())) {
                    hasCompletedCapture = true;
                    captureTime = event.receivedAt();
                    break;
                }
            }
        }

        if (!hasCompletedCapture) {
            return List.of();
        }

        // Check grace period
        Instant effectiveCaptureTime = captureTime != null ? captureTime : shopOrder.createdAt();
        if (effectiveCaptureTime != null && snapshot.gracePeriod() != null) {
            Instant graceDeadline = effectiveCaptureTime.plus(snapshot.gracePeriod());
            if (snapshot.evaluatedAt().isBefore(graceDeadline)) {
                // Still within the grace period; do not flag as an exception yet
                return List.of();
            }
        }

        String paypalOrderId = shopOrder.paypalOrderId() != null ? shopOrder.paypalOrderId() : "PP-UNKNOWN";
        Money orderAmount = shopOrder.total();
        Money discrepancyAmount = capturedMoney != null ? capturedMoney : orderAmount;

        String summary = String.format(
            "PayPal capture completed, but merchant order %s remains %s after grace period.",
            shopOrder.id(), shopOrder.status()
        );

        List<DetectionFinding> findings = new ArrayList<>();
        findings.add(DetectionFinding.of(
            CaseType.PAID_NOT_MARKED,
            CaseSeverity.HIGH,
            paypalOrderId,
            shopOrder.id(),
            orderAmount,
            discrepancyAmount,
            summary
        ));

        return findings;
    }
}
