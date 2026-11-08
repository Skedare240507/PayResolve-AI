package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payresolve.common.Money;

import java.util.List;

/**
 * Payload for creating an Order in PayPal Orders v2 API.
 */
public record CreateOrderRequest(
    @JsonProperty("intent") String intent,
    @JsonProperty("purchase_units") List<PurchaseUnitRequest> purchaseUnits,
    @JsonProperty("application_context") ApplicationContext applicationContext
) {
    public static CreateOrderRequest of(String shopOrderId, Money amount, String returnUrl, String cancelUrl) {
        PurchaseUnitRequest unit = new PurchaseUnitRequest(
            "default",
            shopOrderId,
            Amount.fromMoney(amount)
        );
        ApplicationContext appCtx = new ApplicationContext(returnUrl, cancelUrl, "PAY_NOW");
        return new CreateOrderRequest("CAPTURE", List.of(unit), appCtx);
    }

    public record PurchaseUnitRequest(
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("custom_id") String customId,
        @JsonProperty("amount") Amount amount
    ) {}

    public record ApplicationContext(
        @JsonProperty("return_url") String returnUrl,
        @JsonProperty("cancel_url") String cancelUrl,
        @JsonProperty("user_action") String userAction
    ) {}
}
