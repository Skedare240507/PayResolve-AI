package com.payresolve.paypal;

import com.payresolve.paypal.dto.DiagnosticCheckDto;
import com.payresolve.paypal.dto.PayPalDiagnosticsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayPalDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayPalDiagnosticsService diagnosticsService;

    @Test
    @DisplayName("Unauthenticated caller receives HTTP 401 Unauthorized")
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/paypal/diagnostics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("VIEWER role receives HTTP 403 Forbidden")
    void viewerRejected() throws Exception {
        mockMvc.perform(get("/api/paypal/diagnostics")
                .with(user("viewer").roles("VIEWER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPERATOR role receives HTTP 200 OK with diagnostics checklist")
    void operatorAllowed() throws Exception {
        Instant now = Instant.parse("2026-11-11T12:00:00Z");
        when(diagnosticsService.runDiagnostics()).thenReturn(new PayPalDiagnosticsDto(
            true,
            now,
            "https://api-m.sandbox.paypal.com",
            "https://payresolve-api.onrender.com/api/webhooks/paypal",
            "APP-SANDBOX-1",
            null,
            now.minusSeconds(30),
            List.of(
                new DiagnosticCheckDto("sandbox_base_url", "PayPal Sandbox Base URL", "PASS", true, "Sandbox OK", null)
            )
        ));

        mockMvc.perform(get("/api/paypal/diagnostics")
                .with(user("operator").roles("OPERATOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overallOk").value(true))
            .andExpect(jsonPath("$.appId").value("APP-SANDBOX-1"))
            .andExpect(jsonPath("$.checks[0].id").value("sandbox_base_url"))
            .andExpect(jsonPath("$.checks[0].passed").value(true));
    }
}

