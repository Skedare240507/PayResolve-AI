package com.payresolve.paypal;

import com.payresolve.paypal.dto.PayPalDiagnosticsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator-only diagnostics endpoint verifying PayPal Sandbox configuration,
 * OAuth credentials, registered webhooks, and last webhook arrival without exposing secrets.
 */
@RestController
@RequestMapping("/paypal")
@Tag(name = "PayPal Diagnostics", description = "Operator-only live PayPal Sandbox configuration and webhook diagnostics")
public class PayPalDiagnosticsController {

    private final PayPalDiagnosticsService diagnosticsService;

    public PayPalDiagnosticsController(PayPalDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Run PayPal Sandbox configuration and webhook checks (OPERATOR only)")
    public PayPalDiagnosticsDto getDiagnostics() {
        return diagnosticsService.runDiagnostics();
    }
}

