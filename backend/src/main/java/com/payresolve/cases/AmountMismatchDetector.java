package com.payresolve.cases;

import com.payresolve.common.Money;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.ShopOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detector for AMOUNT_MISMATCH (P0):
 * Discrepancy occurs when the captured payment amount or currency differs
 * from the merchant shop order total in minor units.
 */
@Component
public class AmountMismatchDetector implements Detector {

    @Override
    public CaseType supportedType() {
        return CaseType.AMOUNT_MISMATCH;
    }

    @Override
    public List<DetectionFinding> detect(DetectionSnapshot snapshot) {
        ShopOrder shopOrder = snapshot.shopOrder();
        if (shopOrder == null) {
            return List.of();
        }

        List<DetectionFinding> findings = new ArrayList<>();
        String paypalOrderId = shopOrder.paypalOrderId() != null ? shopOrder.paypalOrderId() : "PP-UNKNOWN";

        // Check payments recorded in shop
        for (PaymentRecord payment : snapshot.shopPayments()) {
            if ("COMPLETED".equalsIgnoreCase(payment.status())) {
                evaluateAmountMismatch(shopOrder, paypalOrderId, payment.amount(), findings);
            }
        }

        // If no findings from recorded payments, check PayPal captures in snapshot
        if (findings.isEmpty()) {
            for (CaptureSummary capture : snapshot.paypalCaptures()) {
                if ("COMPLETED".equalsIgnoreCase(capture.status()) && capture.amount() != null) {
                    Money captured = Money.parse(capture.amount().value(), capture.amount().currencyCode());
                    evaluateAmountMismatch(shopOrder, paypalOrderId, captured, findings);
                }
            }
        }

        return findings;
    }

    private void evaluateAmountMismatch(ShopOrder shopOrder, String paypalOrderId, Money captured, List<DetectionFinding> findings) {
        Money orderTotal = shopOrder.total();

        // 1. Currency mismatch invariant
        if (!orderTotal.currency().equalsIgnoreCase(captured.currency())) {
            String summary = String.format(
                "Currency mismatch: shop order %s is in %s, but PayPal captured %s.",
                shopOrder.id(), orderTotal.currency(), captured.currency()
            );
            findings.add(DetectionFinding.of(
                CaseType.AMOUNT_MISMATCH,
                CaseSeverity.HIGH,
                paypalOrderId,
                shopOrder.id(),
                orderTotal,
                captured,
                summary
            ));
            return;
        }

        // 2. Minor units amount mismatch
        if (orderTotal.amountMinor() != captured.amountMinor()) {
            String summary = String.format(
                "Captured amount mismatch: shop order total is %s, but PayPal captured %s (%s discrepancy).",
                orderTotal, captured, orderTotal.difference(captured)
            );
            findings.add(DetectionFinding.of(
                CaseType.AMOUNT_MISMATCH,
                CaseSeverity.HIGH,
                paypalOrderId,
                shopOrder.id(),
                orderTotal,
                captured,
                summary
            ));
        }
    }
}
