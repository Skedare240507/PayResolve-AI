package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShopOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detector for REFUND_MISMATCH (P1):
 * Discrepancy occurs when PayPal refund state differs from the merchant shop refund records
 * (e.g. missing refund record, status mismatch, or amount discrepancy).
 */
@Component
public class RefundMismatchDetector implements Detector {

    @Override
    public CaseType supportedType() {
        return CaseType.REFUND_MISMATCH;
    }

    @Override
    public List<DetectionFinding> detect(DetectionSnapshot snapshot) {
        ShopOrder shopOrder = snapshot.shopOrder();
        if (shopOrder == null) {
            return List.of();
        }

        String paypalOrderId = shopOrder.paypalOrderId() != null ? shopOrder.paypalOrderId() : "PP-UNKNOWN";
        String shopOrderId = shopOrder.id();
        Money orderAmount = shopOrder.total();

        List<RefundRecord> shopRefunds = snapshot.shopRefunds();
        List<RefundRecord> paypalRefunds = snapshot.paypalRefunds();

        // Check if PayPal webhook event indicates a refund
        boolean hasPaypalRefundEvent = false;
        String eventRefundId = null;
        for (WebhookEvent event : snapshot.webhookEvents()) {
            if ("PAYMENT.CAPTURE.REFUNDED".equalsIgnoreCase(event.eventType())) {
                hasPaypalRefundEvent = true;
                eventRefundId = event.resourceId();
                break;
            }
        }

        List<DetectionFinding> findings = new ArrayList<>();

        // Case 1: PayPal processed a refund, but merchant shop has no record
        if ((!paypalRefunds.isEmpty() || hasPaypalRefundEvent) && shopRefunds.isEmpty()) {
            String refId = !paypalRefunds.isEmpty() ? paypalRefunds.getFirst().id() : (eventRefundId != null ? eventRefundId : "REF-UNKNOWN");
            Money refundAmount = !paypalRefunds.isEmpty() ? paypalRefunds.getFirst().amount() : null;
            String summary = String.format(
                "PayPal processed refund %s, but merchant shop has no record of refund for order %s.",
                refId, shopOrderId
            );
            findings.add(DetectionFinding.of(
                CaseType.REFUND_MISMATCH,
                CaseSeverity.MEDIUM,
                paypalOrderId,
                shopOrderId,
                orderAmount,
                refundAmount,
                summary
            ));
            return findings;
        }

        // Case 2: Merchant shop recorded a refund, but PayPal has no corresponding refund
        if (!shopRefunds.isEmpty() && paypalRefunds.isEmpty() && !hasPaypalRefundEvent) {
            RefundRecord shopRefund = shopRefunds.getFirst();
            String summary = String.format(
                "Merchant shop recorded refund %s (%s), but no corresponding refund was found in PayPal.",
                shopRefund.id(), shopRefund.amount()
            );
            findings.add(DetectionFinding.of(
                CaseType.REFUND_MISMATCH,
                CaseSeverity.MEDIUM,
                paypalOrderId,
                shopOrderId,
                orderAmount,
                shopRefund.amount(),
                summary
            ));
            return findings;
        }

        // Case 3: Both have refunds, verify amounts match
        if (!shopRefunds.isEmpty() && !paypalRefunds.isEmpty()) {
            RefundRecord shopRefund = shopRefunds.getFirst();
            RefundRecord paypalRefund = paypalRefunds.getFirst();

            if (!shopRefund.currency().equalsIgnoreCase(paypalRefund.currency())) {
                String summary = String.format(
                    "Refund currency mismatch: shop recorded %s, but PayPal processed %s.",
                    shopRefund.currency(), paypalRefund.currency()
                );
                findings.add(DetectionFinding.of(
                    CaseType.REFUND_MISMATCH,
                    CaseSeverity.MEDIUM,
                    paypalOrderId,
                    shopOrderId,
                    orderAmount,
                    paypalRefund.amount(),
                    summary
                ));
                return findings;
            }

            if (shopRefund.amountMinor() != paypalRefund.amountMinor()) {
                String summary = String.format(
                    "Refund amount mismatch: shop recorded %s, but PayPal processed %s.",
                    shopRefund.amount(), paypalRefund.amount()
                );
                findings.add(DetectionFinding.of(
                    CaseType.REFUND_MISMATCH,
                    CaseSeverity.MEDIUM,
                    paypalOrderId,
                    shopOrderId,
                    orderAmount,
                    paypalRefund.amount(),
                    summary
                ));
                return findings;
            }
        }

        return findings;
    }
}
