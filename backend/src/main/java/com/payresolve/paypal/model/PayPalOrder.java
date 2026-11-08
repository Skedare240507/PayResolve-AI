package com.payresolve.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PayPal Order resource returned by the Orders v2 API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalOrder(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("intent") String intent,
    @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits,
    @JsonProperty("links") List<LinkDescription> links
) {
    public PayPalOrder {
        if (purchaseUnits == null) {
            purchaseUnits = Collections.emptyList();
        }
        if (links == null) {
            links = Collections.emptyList();
        }
    }

    /**
     * Finds the buyer approve URL link ("rel" = "approve" or "payer-action").
     */
    public Optional<String> findApproveUrl() {
        return links.stream()
            .filter(l -> "approve".equalsIgnoreCase(l.rel()) || "payer-action".equalsIgnoreCase(l.rel()))
            .map(LinkDescription::href)
            .findFirst();
    }

    /**
     * Extracts the custom_id (e.g. shop order ID) from the primary purchase unit.
     */
    public Optional<String> findCustomId() {
        return purchaseUnits.stream()
            .map(PurchaseUnit::customId)
            .filter(c -> c != null && !c.isBlank())
            .findFirst();
    }

    /**
     * Extracts the primary capture ID if the order has been captured.
     */
    public Optional<String> findFirstCaptureId() {
        return purchaseUnits.stream()
            .filter(u -> u.payments() != null && u.payments().captures() != null)
            .flatMap(u -> u.payments().captures().stream())
            .map(CaptureSummary::id)
            .findFirst();
    }
}
