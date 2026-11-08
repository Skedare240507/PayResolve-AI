package com.payresolve.paypal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.payresolve.common.Money;
import com.payresolve.paypal.model.Amount;
import com.payresolve.paypal.model.CaptureDetails;
import com.payresolve.paypal.model.DisputeDetail;
import com.payresolve.paypal.model.DisputeListResponse;
import com.payresolve.paypal.model.PayPalOrder;
import com.payresolve.paypal.model.RefundDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class PayPalClientsWireMockTest {

    private WireMockServer wireMockServer;
    private PayPalProperties properties;
    private Clock fixedClock;
    private PayPalAuthService authService;
    private PayPalOrdersClient ordersClient;
    private PayPalPaymentsClient paymentsClient;
    private PayPalWebhookVerificationClient webhookVerificationClient;
    private LivePayPalDisputesClient disputesClient;
    private SyntheticDisputesProvider syntheticProvider;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        String baseUrl = "http://localhost:" + wireMockServer.port();
        properties = new PayPalProperties(
            baseUrl,
            "test-client-id",
            "test-client-secret",
            "test-webhook-id",
            5000,
            10000,
            false
        );

        fixedClock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);

        authService = new PayPalAuthService(properties, fixedClock);
        PayPalConfig config = new PayPalConfig();
        RestClient restClient = config.payPalRestClient(properties, authService);

        ordersClient = new PayPalOrdersClient(restClient);
        paymentsClient = new PayPalPaymentsClient(restClient);
        webhookVerificationClient = new PayPalWebhookVerificationClient(restClient, properties);
        syntheticProvider = new SyntheticDisputesProvider();
        disputesClient = new LivePayPalDisputesClient(restClient, properties, syntheticProvider);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    private void stubOAuthSuccess(String accessToken, int expiresIn) {
        wireMockServer.stubFor(post(urlEqualTo("/v1/oauth2/token"))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                      "access_token": "%s",
                      "token_type": "Bearer",
                      "app_id": "APP-TEST-123",
                      "expires_in": %d,
                      "scope": "https://api.paypal.com/v1/payments/.*"
                    }
                    """, accessToken, expiresIn))));
    }

    @Test
    @DisplayName("OAuth token client caches token in-memory and does not invoke API on subsequent calls")
    void testSuccessfulTokenAcquisitionAndCache() {
        stubOAuthSuccess("TEST-BEARER-TOKEN-1", 3600);

        String token1 = authService.getValidAccessToken();
        String token2 = authService.getValidAccessToken();

        assertThat(token1).isEqualTo("TEST-BEARER-TOKEN-1");
        assertThat(token2).isEqualTo("TEST-BEARER-TOKEN-1");

        // Verify endpoint was only called once
        verify(1, postRequestedFor(urlEqualTo("/v1/oauth2/token")));
    }

    @Test
    @DisplayName("401 Unauthorized automatically invalidates cached token, refreshes token, and retries request")
    void testTokenRefreshOn401() {
        // OAuth token stub returns EXPIRED-TOKEN first, then FRESH-TOKEN
        wireMockServer.stubFor(post(urlEqualTo("/v1/oauth2/token"))
            .inScenario("TokenRefresh")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "access_token": "EXPIRED-TOKEN", "token_type": "Bearer", "expires_in": 3600 }
                    """))
            .willSetStateTo("TokenRefreshed"));

        wireMockServer.stubFor(post(urlEqualTo("/v1/oauth2/token"))
            .inScenario("TokenRefresh")
            .whenScenarioStateIs("TokenRefreshed")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "access_token": "FRESH-TOKEN", "token_type": "Bearer", "expires_in": 3600 }
                    """)));

        // Order GET returns 401 when called with EXPIRED-TOKEN, and 200 when called with FRESH-TOKEN
        wireMockServer.stubFor(get(urlEqualTo("/v2/checkout/orders/ORD-401"))
            .withHeader("Authorization", equalTo("Bearer EXPIRED-TOKEN"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"invalid_token\", \"error_description\": \"Token is expired\"}")));

        wireMockServer.stubFor(get(urlEqualTo("/v2/checkout/orders/ORD-401"))
            .withHeader("Authorization", equalTo("Bearer FRESH-TOKEN"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "ORD-401",
                      "status": "APPROVED",
                      "intent": "CAPTURE",
                      "purchase_units": [
                        { "reference_id": "default", "custom_id": "SHOP-ORDER-401" }
                      ]
                    }
                    """)));

        PayPalOrder order = ordersClient.getOrder("ORD-401");

        assertThat(order).isNotNull();
        assertThat(order.id()).isEqualTo("ORD-401");
        assertThat(order.status()).isEqualTo("APPROVED");
        assertThat(order.findCustomId()).contains("SHOP-ORDER-401");

        // Verify OAuth token endpoint was hit twice (initial + refreshed)
        verify(2, postRequestedFor(urlEqualTo("/v1/oauth2/token")));
    }

    @Test
    @DisplayName("Orders v2 create order with custom_id, get order, and capture order server-side")
    void testCreateGetAndCaptureOrder() {
        stubOAuthSuccess("TEST-ORDER-TOKEN", 3600);

        // 1. Create order
        wireMockServer.stubFor(post(urlEqualTo("/v2/checkout/orders"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(containing("\"custom_id\":\"ORD-SHOP-77\""))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "5O190127TN364715T",
                      "status": "CREATED",
                      "intent": "CAPTURE",
                      "purchase_units": [
                        {
                          "reference_id": "default",
                          "custom_id": "ORD-SHOP-77",
                          "amount": { "currency_code": "USD", "value": "84.50" }
                        }
                      ],
                      "links": [
                        {
                          "href": "https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T",
                          "rel": "approve",
                          "method": "GET"
                        }
                      ]
                    }
                    """)));

        PayPalOrder created = ordersClient.createOrder(
            "ORD-SHOP-77",
            Money.of(8450L, "USD"),
            "http://localhost:5173/shop/success",
            "http://localhost:5173/shop/cancel"
        );

        assertThat(created.id()).isEqualTo("5O190127TN364715T");
        assertThat(created.status()).isEqualTo("CREATED");
        assertThat(created.findCustomId()).contains("ORD-SHOP-77");
        assertThat(created.findApproveUrl()).contains("https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T");

        // 2. Get order
        wireMockServer.stubFor(get(urlEqualTo("/v2/checkout/orders/5O190127TN364715T"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "5O190127TN364715T",
                      "status": "APPROVED",
                      "intent": "CAPTURE",
                      "purchase_units": [
                        {
                          "reference_id": "default",
                          "custom_id": "ORD-SHOP-77",
                          "amount": { "currency_code": "USD", "value": "84.50" }
                        }
                      ]
                    }
                    """)));

        PayPalOrder fetched = ordersClient.getOrder("5O190127TN364715T");
        assertThat(fetched.status()).isEqualTo("APPROVED");

        // 3. Capture order
        wireMockServer.stubFor(post(urlEqualTo("/v2/checkout/orders/5O190127TN364715T/capture"))
            .withHeader("Prefer", equalTo("return=representation"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "5O190127TN364715T",
                      "status": "COMPLETED",
                      "purchase_units": [
                        {
                          "reference_id": "default",
                          "payments": {
                            "captures": [
                              {
                                "id": "2GG279541U949331W",
                                "status": "COMPLETED",
                                "amount": { "currency_code": "USD", "value": "84.50" },
                                "final_capture": true
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """)));

        PayPalOrder captured = ordersClient.captureOrder("5O190127TN364715T");
        assertThat(captured.status()).isEqualTo("COMPLETED");
        assertThat(captured.findFirstCaptureId()).contains("2GG279541U949331W");
    }

    @Test
    @DisplayName("Payments client retrieves capture and refund details (read-only)")
    void testGetCaptureAndRefundDetails() {
        stubOAuthSuccess("TEST-PAYMENT-TOKEN", 3600);

        // Capture query
        wireMockServer.stubFor(get(urlEqualTo("/v2/payments/captures/2GG279541U949331W"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "2GG279541U949331W",
                      "status": "COMPLETED",
                      "amount": { "currency_code": "USD", "value": "84.50" },
                      "final_capture": true,
                      "custom_id": "ORD-SHOP-77",
                      "seller_receivable_breakdown": {
                        "gross_amount": { "currency_code": "USD", "value": "84.50" },
                        "paypal_fee": { "currency_code": "USD", "value": "2.75" },
                        "net_amount": { "currency_code": "USD", "value": "81.75" }
                      },
                      "create_time": "2026-11-07T12:05:00Z"
                    }
                    """)));

        CaptureDetails capture = paymentsClient.getCapture("2GG279541U949331W");
        assertThat(capture.id()).isEqualTo("2GG279541U949331W");
        assertThat(capture.status()).isEqualTo("COMPLETED");
        assertThat(capture.amount().toMoney()).isEqualTo(Money.of(8450L, "USD"));
        assertThat(capture.customId()).isEqualTo("ORD-SHOP-77");
        assertThat(capture.sellerReceivableBreakdown().netAmount().toMoney()).isEqualTo(Money.of(8175L, "USD"));

        // Refund query
        wireMockServer.stubFor(get(urlEqualTo("/v2/payments/refunds/REF-998877"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "REF-998877",
                      "status": "COMPLETED",
                      "amount": { "currency_code": "USD", "value": "84.50" },
                      "note_to_payer": "Customer return acknowledged",
                      "create_time": "2026-11-07T12:30:00Z"
                    }
                    """)));

        RefundDetails refund = paymentsClient.getRefund("REF-998877");
        assertThat(refund.id()).isEqualTo("REF-998877");
        assertThat(refund.status()).isEqualTo("COMPLETED");
        assertThat(refund.amount().toMoney()).isEqualTo(Money.of(8450L, "USD"));
        assertThat(refund.noteToPayer()).isEqualTo("Customer return acknowledged");
    }

    @Test
    @DisplayName("Webhook signature verification handles SUCCESS and FAILURE responses")
    void testWebhookSignatureVerification() {
        stubOAuthSuccess("TEST-VERIFY-TOKEN", 3600);

        Map<String, String> headers = Map.of(
            "PAYPAL-AUTH-ALGO", "SHA256withRSA",
            "PAYPAL-CERT-URL", "https://api-m.sandbox.paypal.com/v1/notifications/certs/CERT-360002",
            "PAYPAL-TRANSMISSION-ID", "TRANS-12345",
            "PAYPAL-TRANSMISSION-SIG", "TEST-SIG==",
            "PAYPAL-TRANSMISSION-TIME", "2026-11-07T12:10:00Z"
        );
        Map<String, Object> eventBody = Map.of("id", "WH-EVENT-1", "event_type", "PAYMENT.CAPTURE.COMPLETED");

        // 1. Success response
        wireMockServer.stubFor(post(urlEqualTo("/v1/notifications/verify-webhook-signature"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"verification_status\": \"SUCCESS\"}")));

        boolean verified = webhookVerificationClient.verifySignature(headers, eventBody);
        assertThat(verified).isTrue();

        // 2. Failure response
        wireMockServer.stubFor(post(urlEqualTo("/v1/notifications/verify-webhook-signature"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"verification_status\": \"FAILURE\"}")));

        boolean failedVerify = webhookVerificationClient.verifySignature(headers, eventBody);
        assertThat(failedVerify).isFalse();
    }

    @Test
    @DisplayName("Transient 5xx failure retries once with jitter and succeeds")
    void testTransientErrorRetryWithJitter() {
        stubOAuthSuccess("TEST-RETRY-TOKEN", 3600);

        wireMockServer.stubFor(get(urlEqualTo("/v2/payments/captures/CAP-RETRY"))
            .inScenario("TransientRetry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Temporarily Unavailable"))
            .willSetStateTo("Retried"));

        wireMockServer.stubFor(get(urlEqualTo("/v2/payments/captures/CAP-RETRY"))
            .inScenario("TransientRetry")
            .whenScenarioStateIs("Retried")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "CAP-RETRY",
                      "status": "COMPLETED",
                      "amount": { "currency_code": "USD", "value": "50.00" }
                    }
                    """)));

        CaptureDetails capture = paymentsClient.getCapture("CAP-RETRY");
        assertThat(capture.id()).isEqualTo("CAP-RETRY");
        assertThat(capture.status()).isEqualTo("COMPLETED");

        // Verify request was sent twice (1 initial + 1 retry)
        verify(2, getRequestedFor(urlEqualTo("/v2/payments/captures/CAP-RETRY")));
    }

    @Test
    @DisplayName("Disputes query succeeds on live API when available, and falls back to synthetic fixtures on 403")
    void testDisputesLiveAndSyntheticFallback() {
        stubOAuthSuccess("TEST-DISPUTE-TOKEN", 3600);

        // 1. Live disputes list succeeds
        wireMockServer.stubFor(get(urlEqualTo("/v1/customer/disputes"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "items": [
                        {
                          "dispute_id": "PP-D-LIVE-1",
                          "reason": "MERCHANDISE_OR_SERVICE_NOT_RECEIVED",
                          "status": "UNDER_REVIEW",
                          "dispute_state": "REQUIRED_ACTION",
                          "dispute_amount": { "currency_code": "USD", "value": "84.50" }
                        }
                      ],
                      "links": []
                    }
                    """)));

        DisputeListResponse liveList = disputesClient.listDisputes();
        assertThat(liveList.synthetic()).isFalse();
        assertThat(liveList.source()).isEqualTo("PAYPAL");
        assertThat(liveList.items()).hasSize(1);
        assertThat(liveList.items().get(0).disputeId()).isEqualTo("PP-D-LIVE-1");
        assertThat(liveList.items().get(0).synthetic()).isFalse();

        // 2. Dispute details returns 403 Forbidden (common sandbox scope restriction)
        wireMockServer.stubFor(get(urlEqualTo("/v1/customer/disputes/PP-D-FORBIDDEN"))
            .willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"insufficient_scope\", \"error_description\": \"Access token missing disputes scope\"}")));

        DisputeDetail fallback = disputesClient.getDispute("PP-D-FORBIDDEN");
        assertThat(fallback).isNotNull();
        assertThat(fallback.synthetic()).isTrue();
        assertThat(fallback.source()).isEqualTo("SYNTHETIC");
    }

    @Test
    @DisplayName("Amount conversion correctly parses and serializes minor units without float drift")
    void testAmountAndMoneyConversions() {
        Money usd = Money.of(8450L, "USD");
        Amount amountUsd = Amount.fromMoney(usd);
        assertThat(amountUsd.currencyCode()).isEqualTo("USD");
        assertThat(amountUsd.value()).isEqualTo("84.50");
        assertThat(amountUsd.toMoney()).isEqualTo(usd);

        Money zero = Money.of(0L, "USD");
        Amount amountZero = Amount.fromMoney(zero);
        assertThat(amountZero.value()).isEqualTo("0.00");
        assertThat(amountZero.toMoney()).isEqualTo(zero);

        Money jpy = Money.of(1500L, "JPY");
        Amount amountJpy = Amount.fromMoney(jpy);
        assertThat(amountJpy.currencyCode()).isEqualTo("JPY");
        assertThat(amountJpy.value()).isEqualTo("1500");
        assertThat(amountJpy.toMoney()).isEqualTo(jpy);
    }
}
