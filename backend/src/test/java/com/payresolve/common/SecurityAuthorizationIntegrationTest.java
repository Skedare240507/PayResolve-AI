package com.payresolve.common;

import com.payresolve.cases.CaseService;
import com.payresolve.paypal.PayPalWebhookVerificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "payresolve.cors.allowed-origins=example.com,https://app.payresolve.test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CaseService caseService;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PayPalWebhookVerificationClient verificationClient;

    @BeforeEach
    void setUp() {
        caseService.seedDemoCases();
    }

    @Test
    @DisplayName("Anonymous callers can access public endpoints and get 401 on every protected endpoint")
    void anonymousAccessMatrix() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(false);

        // Public endpoints succeed
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/api/shop/products"))
            .andExpect(status().isOk());

        MvcResult csrfRes = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();
        assertThat(csrfRes.getResponse().getCookie("XSRF-TOKEN")).isNotNull();

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"WH-ANON-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}"))
            .andExpect(status().isOk());

        // Protected GET endpoints return 401 for anonymous callers
        String[] protectedGets = {
            "/api/auth/me",
            "/api/cases",
            "/api/cases/PR-0142",
            "/api/cases/PR-0142/graph",
            "/api/cases/PR-0142/timeline",
            "/api/cases/PR-0142/actions",
            "/api/cases/PR-0142/attachments",
            "/api/cases/PR-0142/investigations/latest",
            "/api/cases/PR-0142/llm-calls",
            "/api/actions",
            "/api/audit",
            "/api/events",
            "/api/events/stats",
            "/api/benchmark/latest",
            "/api/stats/overview",
            "/api/demo/faults",
            "/api/shop/orders",
            "/api/paypal/diagnostics",
            "/api/llm-calls/recent",
            "/v3/api-docs",
            "/swagger-ui.html"
        };
        for (String path : protectedGets) {
            mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
        }

        // Protected POST/PATCH endpoints (with CSRF) return 401 for anonymous callers
        mockMvc.perform(post("/api/cases/PR-0142/investigate").with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/agent/chat").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"status?\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cases/detect").with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/cases/PR-0142/status").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVESTIGATING\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/benchmark/run").with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/demo/faults/reset").with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/shop/orders").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"mug\",\"quantity\":1}],\"customerLabel\":\"Anon\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("VIEWER can read GET endpoints + investigate/chat, and gets 403 on all OPERATOR endpoints")
    void viewerAccessMatrix() throws Exception {
        // Allowed for VIEWER
        mockMvc.perform(get("/api/cases").with(user("viewer").roles("VIEWER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/stats/overview").with(user("viewer").roles("VIEWER")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/cases/PR-0142/investigate")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf()))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/chat")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"How many open cases?\"}"))
            .andExpect(status().isOk());

        // OPERATOR-only GET endpoints rejected for VIEWER
        mockMvc.perform(get("/api/paypal/diagnostics").with(user("viewer").roles("VIEWER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/v3/api-docs").with(user("viewer").roles("VIEWER")))
            .andExpect(status().isForbidden());

        // OPERATOR-only write endpoints rejected for VIEWER with 403
        mockMvc.perform(post("/api/cases/detect").with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cases/seed").with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/cases/PR-0142/status")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVESTIGATING\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cases/PR-0142/actions")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"HOLD_REFUND\",\"payload\":{},\"idempotencyKey\":\"k1\",\"createdBy\":\"viewer\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/actions/1/approve").with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/actions/1/reject").with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cases/PR-0142/attachments/att-1/analyze")
                .with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/demo/faults")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"failWebhookHandler\":false,\"dropWebhook\":false,\"alterOrderTotal\":false,\"duplicateDelivery\":false}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/demo/faults/reset").with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/benchmark/run").with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/events/WH-SMOKE-001/replay").with(user("viewer").roles("VIEWER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/shop/orders")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"mug\",\"quantity\":1}],\"customerLabel\":\"Viewer\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPERATOR succeeds on protected operator endpoints and POST without CSRF fails with 403")
    void operatorAndCsrfEnforcement() throws Exception {
        // POST without CSRF token returns 403 on protected routes (including auth login & operator writes)
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"operator\",\"password\":\"test_operator_password\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:payresolve:csrf-invalid"));

        mockMvc.perform(post("/api/cases/detect")
                .with(user("operator").roles("OPERATOR")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:payresolve:csrf-invalid"));

        mockMvc.perform(post("/api/cases/PR-0142/investigate")
                .with(user("viewer").roles("VIEWER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:payresolve:csrf-invalid"));

        // OPERATOR succeeds when CSRF token is attached
        mockMvc.perform(post("/api/cases/detect")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/demo/faults/reset")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/benchmark/run")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs")
                .with(user("operator").roles("OPERATOR")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CORS origins are exact without expanding scheme-less entries into http/https")
    void corsDoesNotExpandSchemelessOrigins() {
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/cases"));
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins())
            .containsExactly("example.com", "https://app.payresolve.test")
            .doesNotContain("https://example.com", "http://example.com");
    }
}

