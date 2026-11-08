package com.payresolve.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing demo fault injection switches for testing payment exception workflows.
 */
@RestController
@RequestMapping("/demo/faults")
@Tag(name = "Demo Faults", description = "Fault injection switches for demo scenarios")
public class DemoFaultController {

    private static final Logger log = LoggerFactory.getLogger(DemoFaultController.class);
    private final DemoFaultSwitches faultSwitches;

    public DemoFaultController(DemoFaultSwitches faultSwitches) {
        this.faultSwitches = faultSwitches;
    }

    @GetMapping
    @Operation(summary = "Get current demo fault switches state")
    public DemoFaultsDto getFaults() {
        return DemoFaultsDto.fromSwitches(faultSwitches);
    }

    @PostMapping
    @Operation(summary = "Update demo fault switches")
    public DemoFaultsDto updateFaults(@RequestBody DemoFaultsDto dto) {
        if (dto.failWebhookHandler() != null) {
            faultSwitches.setFailWebhookHandler(dto.failWebhookHandler());
        }
        if (dto.dropWebhook() != null) {
            faultSwitches.setDropWebhook(dto.dropWebhook());
        }
        if (dto.duplicateDelivery() != null) {
            faultSwitches.setDuplicateDelivery(dto.duplicateDelivery());
        }
        if (dto.alterOrderTotal() != null) {
            faultSwitches.setAlterOrderTotal(dto.alterOrderTotal());
        }
        log.info("Updated demo fault switches: failHandler={}, dropWebhook={}, duplicateDelivery={}, alterOrderTotal={}",
            faultSwitches.isFailWebhookHandler(), faultSwitches.isDropWebhook(),
            faultSwitches.isDuplicateDelivery(), faultSwitches.isAlterOrderTotal());
        return DemoFaultsDto.fromSwitches(faultSwitches);
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset all demo fault switches to false")
    public DemoFaultsDto resetFaults() {
        faultSwitches.reset();
        log.info("Reset all demo fault switches to false");
        return DemoFaultsDto.fromSwitches(faultSwitches);
    }
}
