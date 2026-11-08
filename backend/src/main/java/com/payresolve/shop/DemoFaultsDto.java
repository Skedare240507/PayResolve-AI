package com.payresolve.shop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer object for configuring demo fault injection switches.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DemoFaultsDto(
    @JsonProperty("failWebhookHandler") Boolean failWebhookHandler,
    @JsonProperty("dropWebhook") Boolean dropWebhook,
    @JsonProperty("duplicateDelivery") Boolean duplicateDelivery,
    @JsonProperty("alterOrderTotal") Boolean alterOrderTotal,
    @JsonProperty("demoMode") Boolean demoMode
) {
    public static DemoFaultsDto fromSwitches(DemoFaultSwitches switches) {
        return new DemoFaultsDto(
            switches.isFailWebhookHandler(),
            switches.isDropWebhook(),
            switches.isDuplicateDelivery(),
            switches.isAlterOrderTotal(),
            switches.isDemoMode()
        );
    }
}
