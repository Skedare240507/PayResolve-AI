package com.payresolve.cases;

import com.payresolve.ingest.WebhookEvent;
import com.payresolve.paypal.model.CaptureSummary;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShopOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of relevant PayPal state, shop state, and webhook events
 * evaluated by pure Detector functions.
 */
public record DetectionSnapshot(
    String targetId,
    ShopOrder shopOrder,
    List<PaymentRecord> shopPayments,
    List<RefundRecord> shopRefunds,
    List<RefundRecord> paypalRefunds,
    List<CaptureSummary> paypalCaptures,
    List<WebhookEvent> webhookEvents,
    Instant evaluatedAt,
    Duration gracePeriod
) {
    public DetectionSnapshot {
        shopPayments = shopPayments != null ? List.copyOf(shopPayments) : List.of();
        shopRefunds = shopRefunds != null ? List.copyOf(shopRefunds) : List.of();
        paypalRefunds = paypalRefunds != null ? List.copyOf(paypalRefunds) : List.of();
        paypalCaptures = paypalCaptures != null ? List.copyOf(paypalCaptures) : List.of();
        webhookEvents = webhookEvents != null ? List.copyOf(webhookEvents) : List.of();
        evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
        gracePeriod = gracePeriod != null ? gracePeriod : Duration.ZERO;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String targetId;
        private ShopOrder shopOrder;
        private List<PaymentRecord> shopPayments = new ArrayList<>();
        private List<RefundRecord> shopRefunds = new ArrayList<>();
        private List<RefundRecord> paypalRefunds = new ArrayList<>();
        private List<CaptureSummary> paypalCaptures = new ArrayList<>();
        private List<WebhookEvent> webhookEvents = new ArrayList<>();
        private Instant evaluatedAt;
        private Duration gracePeriod = Duration.ZERO;

        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder shopOrder(ShopOrder shopOrder) {
            this.shopOrder = shopOrder;
            return this;
        }

        public Builder shopPayments(List<PaymentRecord> shopPayments) {
            this.shopPayments = shopPayments != null ? new ArrayList<>(shopPayments) : new ArrayList<>();
            return this;
        }

        public Builder addShopPayment(PaymentRecord payment) {
            this.shopPayments.add(payment);
            return this;
        }

        public Builder shopRefunds(List<RefundRecord> shopRefunds) {
            this.shopRefunds = shopRefunds != null ? new ArrayList<>(shopRefunds) : new ArrayList<>();
            return this;
        }

        public Builder addShopRefund(RefundRecord refund) {
            this.shopRefunds.add(refund);
            return this;
        }

        public Builder paypalRefunds(List<RefundRecord> paypalRefunds) {
            this.paypalRefunds = paypalRefunds != null ? new ArrayList<>(paypalRefunds) : new ArrayList<>();
            return this;
        }

        public Builder addPaypalRefund(RefundRecord refund) {
            this.paypalRefunds.add(refund);
            return this;
        }

        public Builder paypalCaptures(List<CaptureSummary> paypalCaptures) {
            this.paypalCaptures = paypalCaptures != null ? new ArrayList<>(paypalCaptures) : new ArrayList<>();
            return this;
        }

        public Builder addPaypalCapture(CaptureSummary capture) {
            this.paypalCaptures.add(capture);
            return this;
        }

        public Builder webhookEvents(List<WebhookEvent> webhookEvents) {
            this.webhookEvents = webhookEvents != null ? new ArrayList<>(webhookEvents) : new ArrayList<>();
            return this;
        }

        public Builder addWebhookEvent(WebhookEvent event) {
            this.webhookEvents.add(event);
            return this;
        }

        public Builder evaluatedAt(Instant evaluatedAt) {
            this.evaluatedAt = evaluatedAt;
            return this;
        }

        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = gracePeriod;
            return this;
        }

        public DetectionSnapshot build() {
            return new DetectionSnapshot(
                targetId,
                shopOrder,
                shopPayments,
                shopRefunds,
                paypalRefunds,
                paypalCaptures,
                webhookEvents,
                evaluatedAt != null ? evaluatedAt : Instant.now(),
                gracePeriod != null ? gracePeriod : Duration.ZERO
            );
        }
    }
}
