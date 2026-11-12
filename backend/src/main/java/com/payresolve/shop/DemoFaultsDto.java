package com.payresolve.shop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Data transfer object for configuring demo fault injection switches.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DemoFaultsDto(
    @JsonProperty("failWebhookHandler") Boolean failWebhookHandler,
    @JsonProperty("dropWebhook") Boolean dropWebhook,
    @JsonProperty("duplicateDelivery") Boolean duplicateDelivery,
    @JsonProperty("alterOrderTotal") Boolean alterOrderTotal,
    @JsonProperty("demoMode") Boolean demoMode,
    @JsonProperty("resetsAt") Instant resetsAt,
    @JsonProperty("activeFaultNames") List<String> activeFaultNames
) {
    public DemoFaultsDto(
        Boolean failWebhookHandler,
        Boolean dropWebhook,
        Boolean duplicateDelivery,
        Boolean alterOrderTotal,
        Boolean demoMode
    ) {
        this(failWebhookHandler, dropWebhook, duplicateDelivery, alterOrderTotal, demoMode, null, List.of());
    }

    public static DemoFaultsDto fromSwitches(DemoFaultSwitches switches) {
        Instant deadline = switches.getResetsAt();
        return new DemoFaultsDto(
            switches.isFailWebhookHandler(),
            switches.isDropWebhook(),
            switches.isDuplicateDelivery(),
            switches.isAlterOrderTotal(),
            switches.isDemoMode(),
            deadline,
            switches.getActiveFaultNames()
        );
    }
}
