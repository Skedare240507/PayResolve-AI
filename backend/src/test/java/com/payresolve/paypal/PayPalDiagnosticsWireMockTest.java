package com.payresolve.paypal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.payresolve.paypal.dto.DiagnosticCheckDto;
import com.payresolve.paypal.dto.PayPalDiagnosticsDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayPalDiagnosticsWireMockTest {

    private static final String SECRET_CLIENT_ID = "client-id-super-secret-xyz";
    private static final String SECRET_CLIENT_SECRET = "client-secret-super-secret-abc";
    private static final String SECRET_WEBHOOK_ID = "WH-ID-9988776655";
    private static final String SECRET_ACCESS_TOKEN = "A21AA-SECRET-ACCESS-TOKEN-VALUE";

    private WireMockServer wireMockServer;
    private Clock fixedClock;
    private Instant t0;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        t0 = Instant.parse("2026-11-11T12:00:00Z");
        fixedClock = Clock.fixed(t0, ZoneOffset.UTC);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    private PayPalDiagnosticsService serviceWithWireMock(
        String configuredBaseUrl,
        String webhookId,
        String publicUrl,
        Optional<Instant> lastWebhookAt
    ) {
        String wireMockBaseUrl = "http://localhost:" + wireMockServer.port();
        PayPalProperties wireMockProps = new PayPalProperties(
            wireMockBaseUrl,
            SECRET_CLIENT_ID,
            SECRET_CLIENT_SECRET,
            webhookId,
            5000,
            10000,
            false
        );

        PayPalAuthService authService = new PayPalAuthService(wireMockProps, fixedClock);
        RestClient restClient = new PayPalConfig().payPalRestClient(wireMockProps, authService);
        PayPalWebhookManagementClient webhookClient = new PayPalWebhookManagementClient(restClient);

        PayPalProperties checkedProps = new PayPalProperties(
            configuredBaseUrl,
            SECRET_CLIENT_ID,
            SECRET_CLIENT_SECRET,
            webhookId,
            5000,
            10000,
            false
        );

        return new PayPalDiagnosticsService(
            checkedProps,
            authService,
            webhookClient,
            () -> lastWebhookAt,
            publicUrl,
            fixedClock
        );
    }

    private void stubTokenSuccess() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/oauth2/token"))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "access_token": "%s",
                      "token_type": "Bearer",
                      "app_id": "APP-SANDBOX-12345",
                      "expires_in": 32400,
                      "scope": "https://uri.paypal.com/services/payments/payment/read"
                    }
                    """.formatted(SECRET_ACCESS_TOKEN))));
    }

    @Test
    @DisplayName("1. All diagnostics pass with matching sandbox URL, OAuth token, webhook URL & events, and never expose secrets")
    void allChecksPassWithoutExposingSecrets() {
        stubTokenSuccess();
        wireMockServer.stubFor(get(urlEqualTo("/v1/notifications/webhooks"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "webhooks": [
                        {
                          "id": "%s",
                          "url": "https://payresolve-api.onrender.com/api/webhooks/paypal",
                          "event_types": [
                            { "name": "CHECKOUT.ORDER.APPROVED" },
                            { "name": "PAYMENT.CAPTURE.COMPLETED" },
                            { "name": "PAYMENT.CAPTURE.REFUNDED" }
                          ]
                        }
                      ]
                    }
                    """.formatted(SECRET_WEBHOOK_ID))));

        wireMockServer.stubFor(get(urlEqualTo("/v1/identity/oauth2/userinfo?schema=paypalv1.1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "user_id": "https://www.paypal.com/webapps/auth/identity/user/sandbox-user",
                      "name": "PayResolve Sandbox Store",
                      "payer_id": "MERCHANTSB123",
                      "emails": [
                        { "value": "sb-merchant@business.example.com", "primary": true }
                      ]
                    }
                    """)));

        PayPalDiagnosticsService service = serviceWithWireMock(
            "https://api-m.sandbox.paypal.com",
            SECRET_WEBHOOK_ID,
            "https://payresolve-api.onrender.com",
            Optional.of(t0.minusSeconds(90))
        );

        PayPalDiagnosticsDto dto = service.runDiagnostics();

        assertThat(dto.overallOk()).isTrue();
        assertThat(dto.appId()).isEqualTo("APP-SANDBOX-12345");
        assertThat(dto.businessAccount()).isEqualTo("sb-merchant@business.example.com");
        assertThat(dto.lastWebhookReceivedAt()).isEqualTo(t0.minusSeconds(90));
        assertThat(dto.expectedWebhookUrl()).isEqualTo("https://payresolve-api.onrender.com/api/webhooks/paypal");
        assertThat(dto.checks()).hasSize(6);
        assertThat(dto.checks()).allSatisfy(c -> assertThat(c.passed()).isTrue());

        // Strict secret non-disclosure assertions
        String serialized = dto.toString();
        assertThat(serialized).doesNotContain(SECRET_CLIENT_ID);
        assertThat(serialized).doesNotContain(SECRET_CLIENT_SECRET);
        assertThat(serialized).doesNotContain(SECRET_WEBHOOK_ID);
        assertThat(serialized).doesNotContain(SECRET_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("2. Detects missing webhook URL and missing event subscriptions with actionable fix hints")
    void detectsWebhookMismatchAndMissingEvents() {
        stubTokenSuccess();
        wireMockServer.stubFor(get(urlEqualTo("/v1/notifications/webhooks"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "webhooks": [
                        {
                          "id": "WH-OTHER-999",
                          "url": "https://old-tunnel.trycloudflare.com/api/webhooks/paypal",
                          "event_types": [
                            { "name": "PAYMENT.CAPTURE.COMPLETED" }
                          ]
                        }
                      ]
                    }
                    """)));

        wireMockServer.stubFor(get(urlEqualTo("/v1/identity/oauth2/userinfo?schema=paypalv1.1"))
            .willReturn(aResponse().withStatus(403)));

        PayPalDiagnosticsService service = serviceWithWireMock(
            "https://api-m.sandbox.paypal.com",
            SECRET_WEBHOOK_ID,
            "https://payresolve-api.onrender.com",
            Optional.empty()
        );

        PayPalDiagnosticsDto dto = service.runDiagnostics();

        assertThat(dto.overallOk()).isFalse();
        DiagnosticCheckDto webhookCheck = checkById(dto, "webhook_registered");
        assertThat(webhookCheck.passed()).isFalse();
        assertThat(webhookCheck.status()).isEqualTo("FAIL");
        assertThat(webhookCheck.detail()).contains("https://payresolve-api.onrender.com/api/webhooks/paypal");
        assertThat(webhookCheck.fixHint()).contains("PAYMENT.CAPTURE.COMPLETED");

        // UserInfo 403 under client_credentials is non-fatal INFO
        DiagnosticCheckDto accountCheck = checkById(dto, "sandbox_business_account");
        assertThat(accountCheck.passed()).isTrue();
        assertThat(accountCheck.status()).isEqualTo("INFO");
        assertThat(accountCheck.detail()).contains("APP-SANDBOX-12345");

        // Missing last webhook yields WARN with hint
        DiagnosticCheckDto lastWebhookCheck = checkById(dto, "last_webhook_received");
        assertThat(lastWebhookCheck.passed()).isFalse();
        assertThat(lastWebhookCheck.status()).isEqualTo("WARN");
        assertThat(lastWebhookCheck.fixHint()).isNotBlank();
    }

    @Test
    @DisplayName("3. Detects webhook missing required event type even when URL matches")
    void detectsMissingRequiredEventOnMatchingUrl() {
        stubTokenSuccess();
        wireMockServer.stubFor(get(urlEqualTo("/v1/notifications/webhooks"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "webhooks": [
                        {
                          "id": "%s",
                          "url": "https://payresolve-api.onrender.com/api/webhooks/paypal",
                          "event_types": [
                            { "name": "PAYMENT.CAPTURE.COMPLETED" }
                          ]
                        }
                      ]
                    }
                    """.formatted(SECRET_WEBHOOK_ID))));
        wireMockServer.stubFor(get(urlEqualTo("/v1/identity/oauth2/userinfo?schema=paypalv1.1"))
            .willReturn(aResponse().withStatus(401)));

        PayPalDiagnosticsService service = serviceWithWireMock(
            "https://api-m.sandbox.paypal.com",
            SECRET_WEBHOOK_ID,
            "https://payresolve-api.onrender.com",
            Optional.empty()
        );

        PayPalDiagnosticsDto dto = service.runDiagnostics();
        assertThat(dto.overallOk()).isFalse();

        DiagnosticCheckDto webhookCheck = checkById(dto, "webhook_registered");
        assertThat(webhookCheck.passed()).isFalse();
        assertThat(webhookCheck.detail()).contains("PAYMENT.CAPTURE.REFUNDED");
    }

    @Test
    @DisplayName("4. Detects non-sandbox PAYPAL_BASE_URL and failed OAuth credentials cleanly")
    void detectsLiveBaseUrlAndFailedOAuth() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/oauth2/token"))
            .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid_client\"}")));

        PayPalDiagnosticsService service = serviceWithWireMock(
            "https://api-m.paypal.com",
            "",
            "http://localhost:8080",
            Optional.empty()
        );

        PayPalDiagnosticsDto dto = service.runDiagnostics();

        assertThat(dto.overallOk()).isFalse();
        assertThat(checkById(dto, "sandbox_base_url").passed()).isFalse();
        assertThat(checkById(dto, "sandbox_base_url").fixHint()).contains("api-m.sandbox.paypal.com");
        assertThat(checkById(dto, "oauth_token").passed()).isFalse();
        assertThat(checkById(dto, "webhook_id_set").passed()).isFalse();
        assertThat(checkById(dto, "webhook_registered").passed()).isFalse();
        assertThat(dto.toString()).doesNotContain(SECRET_CLIENT_ID);
        assertThat(dto.toString()).doesNotContain(SECRET_CLIENT_SECRET);
    }

    @Test
    @DisplayName("5. Detects mismatched PAYPAL_WEBHOOK_ID against matching PayPal registration without leaking ID")
    void detectsMismatchedWebhookIdWithoutLeakingId() {
        PayPalProperties props = new PayPalProperties(
            "https://api-m.sandbox.paypal.com",
            SECRET_CLIENT_ID,
            SECRET_CLIENT_SECRET,
            "WH-CONFIGURED-111",
            5000,
            10000,
            false
        );
        PayPalAuthService authService = mock(PayPalAuthService.class);
        when(authService.verifyCredentialsAndGetMetadata())
            .thenReturn(new PayPalAuthService.PayPalTokenMetadata("APP-1", "scope", 32000L));

        PayPalWebhookManagementClient client = mock(PayPalWebhookManagementClient.class);
        when(client.listWebhooks()).thenReturn(java.util.List.of(
            new com.payresolve.paypal.model.RegisteredWebhook(
                "WH-ACTUAL-999",
                "https://payresolve-api.onrender.com/api/webhooks/paypal",
                java.util.List.of(new com.payresolve.paypal.model.WebhookEventType("*", "All events", "ENABLED"))
            )
        ));
        when(client.fetchUserInfoIfExposed()).thenReturn(Optional.empty());

        PayPalDiagnosticsService service = new PayPalDiagnosticsService(
            props, authService, client, Optional::empty, "https://payresolve-api.onrender.com", fixedClock
        );

        PayPalDiagnosticsDto dto = service.runDiagnostics();
        assertThat(dto.overallOk()).isFalse();
        DiagnosticCheckDto check = checkById(dto, "webhook_registered");
        assertThat(check.passed()).isFalse();
        assertThat(check.detail()).contains("PAYPAL_WEBHOOK_ID does not match");
        assertThat(dto.toString()).doesNotContain("WH-ACTUAL-999");
        assertThat(dto.toString()).doesNotContain("WH-CONFIGURED-111");
    }

    private static DiagnosticCheckDto checkById(PayPalDiagnosticsDto dto, String id) {
        return dto.checks().stream()
            .filter(c -> id.equals(c.id()))
            .findFirst()
            .orElseThrow();
    }
}

