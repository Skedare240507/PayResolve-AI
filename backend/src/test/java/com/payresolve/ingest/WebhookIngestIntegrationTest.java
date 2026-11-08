package com.payresolve.ingest;

import com.payresolve.common.Money;
import com.payresolve.paypal.PayPalWebhookVerificationClient;
import com.payresolve.shop.DemoFaultSwitches;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.ShopOrder;
import com.payresolve.shop.ShopOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookIngestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEventRepository repository;

    @Autowired
    private WebhookWorkerService workerService;

    @Autowired
    private ShopOrderService shopOrderService;

    @Autowired
    private DemoFaultSwitches faultSwitches;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private PayPalWebhookVerificationClient verificationClient;

    @BeforeEach
    void setUp() {
        faultSwitches.reset();
        jdbcClient.sql("DELETE FROM payments").update();
        jdbcClient.sql("DELETE FROM shop_orders").update();
        jdbcClient.sql("DELETE FROM webhook_events").update();
    }

    private String sampleCaptureCompletedPayload(String eventId, String captureId, String orderId, String amount) {
        return """
            {
              "id": "%s",
              "event_type": "PAYMENT.CAPTURE.COMPLETED",
              "create_time": "2026-11-07T10:02:41Z",
              "resource_type": "capture",
              "resource": {
                "id": "%s",
                "status": "COMPLETED",
                "custom_id": "%s",
                "amount": {
                  "currency_code": "USD",
                  "value": "%s"
                }
              }
            }
            """.formatted(eventId, captureId, orderId, amount);
    }

    @Test
    @DisplayName("1. Valid verified webhook is stored as PENDING and worker transitions order to PAID")
    void shouldIngestValidWebhookAndApplyBusinessEffect() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);

        shopOrderService.createOrder("ORDER-101", "PP-ORD-101", Money.parse("62.50", "USD"), "Alice Buyer");

        String payload = sampleCaptureCompletedPayload("WH-EVT-101", "CAP-101", "ORDER-101", "62.50");

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .header("paypal-auth-algo", "SHA256withRSA")
                .header("paypal-cert-url", "https://api.sandbox.paypal.com/cert.pem")
                .header("paypal-transmission-id", "TX-101")
                .header("paypal-transmission-sig", "dGVzdC1zaWc=")
                .header("paypal-transmission-time", "2026-11-07T10:02:41Z")
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.paypalEventId").value("WH-EVT-101"));

        // Verify stored in DB
        Optional<WebhookEvent> eventOpt = repository.findByPaypalEventId("WH-EVT-101");
        assertThat(eventOpt).isPresent();
        assertThat(eventOpt.get().status()).isEqualTo("PENDING");
        assertThat(eventOpt.get().verified()).isTrue();

        // Run worker
        int processed = workerService.processBatch(10);
        assertThat(processed).isEqualTo(1);

        // Verify business effects
        ShopOrder order = shopOrderService.findOrderById("ORDER-101").orElseThrow();
        assertThat(order.status()).isEqualTo("PAID");

        Optional<PaymentRecord> payment = shopOrderService.findPaymentByCaptureId("CAP-101");
        assertThat(payment).isPresent();
        assertThat(payment.get().amountMinor()).isEqualTo(6250L);
        assertThat(payment.get().currency()).isEqualTo("USD");

        // Webhook event is marked PROCESSED
        WebhookEvent processedEvent = repository.findByPaypalEventId("WH-EVT-101").orElseThrow();
        assertThat(processedEvent.status()).isEqualTo("PROCESSED");
        assertThat(processedEvent.processedAt()).isNotNull();
    }

    @Test
    @DisplayName("2. Unverified webhook is stored with verified=false and never processed by worker")
    void shouldStoreUnverifiedWebhookAndNeverProcess() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(false);

        shopOrderService.createOrder("ORDER-102", "PP-ORD-102", Money.parse("25.00", "USD"), "Bob Fake");
        String payload = sampleCaptureCompletedPayload("WH-EVT-BAD", "CAP-BAD", "ORDER-102", "25.00");

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.verified").value(false));

        WebhookEvent event = repository.findByPaypalEventId("WH-EVT-BAD").orElseThrow();
        assertThat(event.verified()).isFalse();
        assertThat(event.status()).isEqualTo("FAILED");

        // Worker should skip unverified events
        int processed = workerService.processBatch(10);
        assertThat(processed).isEqualTo(0);

        ShopOrder order = shopOrderService.findOrderById("ORDER-102").orElseThrow();
        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(shopOrderService.findPaymentByCaptureId("CAP-BAD")).isEmpty();
    }

    @Test
    @DisplayName("3. Idempotent replay: Replaying the exact same webhook 10 times yields exactly one business effect")
    void shouldIdempotentlyReplayWebhookTenTimesWithExactlyOneEffect() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);

        shopOrderService.createOrder("ORDER-103", "PP-ORD-103", Money.parse("84.50", "USD"), "Charlie Replay");
        String payload = sampleCaptureCompletedPayload("WH-EVT-REPLAY", "CAP-REPLAY", "ORDER-103", "84.50");

        // Replay 10 times via HTTP POST
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/webhooks/paypal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("paypal-auth-algo", "SHA256withRSA")
                    .header("paypal-cert-url", "https://api.sandbox.paypal.com/cert.pem")
                    .header("paypal-transmission-id", "TX-REPLAY")
                    .header("paypal-transmission-sig", "c2ln")
                    .header("paypal-transmission-time", "2026-11-07T10:02:41Z")
                    .content(payload))
                    .andExpect(status().isOk());
        }

        // Exactly 1 webhook event row exists (duplicate event IDs increment delivery_count)
        Integer eventCount = jdbcClient.sql("SELECT COUNT(*) FROM webhook_events WHERE paypal_event_id = 'WH-EVT-REPLAY'")
                .query(Integer.class)
                .single();
        assertThat(eventCount).isEqualTo(1);

        WebhookEvent stored = repository.findByPaypalEventId("WH-EVT-REPLAY").orElseThrow();
        assertThat(stored.deliveryCount()).isEqualTo(10);

        // Process with worker 10 times
        for (int i = 0; i < 10; i++) {
            workerService.processBatch(10);
        }

        // Exactly 1 payment record exists
        Integer paymentCount = jdbcClient.sql("SELECT COUNT(*) FROM payments WHERE paypal_capture_id = 'CAP-REPLAY'")
                .query(Integer.class)
                .single();
        assertThat(paymentCount).isEqualTo(1);

        // Order is PAID
        ShopOrder order = shopOrderService.findOrderById("ORDER-103").orElseThrow();
        assertThat(order.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("4. Fault switch: dropWebhook drops event with 200 fast and inserts nothing")
    void shouldHonorDropWebhookFaultSwitch() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);
        faultSwitches.setDropWebhook(true);

        String payload = sampleCaptureCompletedPayload("WH-EVT-DROPPED", "CAP-DROP", "ORD-DROP", "10.00");

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DROPPED"))
                .andExpect(jsonPath("$.dropped").value(true));

        assertThat(repository.findByPaypalEventId("WH-EVT-DROPPED")).isEmpty();
    }

    @Test
    @DisplayName("5. Fault switch: failWebhookHandler causes worker attempt failure, records error, order stays PENDING")
    void shouldHonorFailWebhookHandlerFaultSwitch() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);

        shopOrderService.createOrder("ORDER-FAIL", "PP-ORD-FAIL", Money.parse("45.00", "USD"), "David Fail");
        String payload = sampleCaptureCompletedPayload("WH-EVT-FAIL", "CAP-FAIL", "ORDER-FAIL", "45.00");

        // Ingest normally
        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .header("paypal-auth-algo", "SHA256withRSA")
                .header("paypal-cert-url", "https://api.sandbox.paypal.com/cert.pem")
                .header("paypal-transmission-id", "TX-FAIL")
                .header("paypal-transmission-sig", "c2ln")
                .header("paypal-transmission-time", "2026-11-07T10:02:41Z")
                .content(payload))
                .andExpect(status().isOk());

        // Activate fault switch before worker runs
        faultSwitches.setFailWebhookHandler(true);

        int processed = workerService.processBatch(10);
        assertThat(processed).isEqualTo(0);

        // Event should have incremented attempt count and recorded last_error
        WebhookEvent event = repository.findByPaypalEventId("WH-EVT-FAIL").orElseThrow();
        assertThat(event.attempts()).isEqualTo(1);
        assertThat(event.lastError()).contains("Simulated handler failure");

        // Order remains PENDING
        ShopOrder order = shopOrderService.findOrderById("ORDER-FAIL").orElseThrow();
        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(shopOrderService.findPaymentByCaptureId("CAP-FAIL")).isEmpty();
    }

    @Test
    @DisplayName("6. Fault switch: duplicateDelivery simulates duplicate arrival safely")
    void shouldHonorDuplicateDeliveryFaultSwitch() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);
        faultSwitches.setDuplicateDelivery(true);

        String payload = sampleCaptureCompletedPayload("WH-EVT-DUP-SW", "CAP-DUP", "ORD-DUP", "30.00");

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .header("paypal-auth-algo", "SHA256withRSA")
                .header("paypal-cert-url", "https://api.sandbox.paypal.com/cert.pem")
                .header("paypal-transmission-id", "TX-DUP")
                .header("paypal-transmission-sig", "c2ln")
                .header("paypal-transmission-time", "2026-11-07T10:02:41Z")
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Only 1 row stored even with duplicate delivery simulation
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM webhook_events WHERE paypal_event_id = 'WH-EVT-DUP-SW'")
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("7. Event Inbox API: GET /api/events, GET /api/events/{id}, and GET /api/events/stats")
    void shouldQueryEventInboxAndDetailAndStats() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);

        shopOrderService.createOrder("ORDER-INBOX", "PP-ORD-INBOX", Money.parse("15.00", "USD"), "Eve Inbox");
        String payload = sampleCaptureCompletedPayload("WH-INBOX-1", "CAP-INBOX-1", "ORDER-INBOX", "15.00");

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .header("paypal-auth-algo", "SHA256withRSA")
                .header("paypal-cert-url", "https://api.sandbox.paypal.com/cert.pem")
                .header("paypal-transmission-id", "TX-INBOX")
                .header("paypal-transmission-sig", "c2ln")
                .header("paypal-transmission-time", "2026-11-07T10:02:41Z")
                .content(payload))
                .andExpect(status().isOk());

        workerService.processBatch(10);

        // 1. List events
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("WH-INBOX-1"))
                .andExpect(jsonPath("$[0].type").value("PAYMENT.CAPTURE.COMPLETED"))
                .andExpect(jsonPath("$[0].signature").value("Verified"))
                .andExpect(jsonPath("$[0].status").value("Processed"))
                .andExpect(jsonPath("$[0].attempts[0].label").value("Attempt 1"))
                .andExpect(jsonPath("$[0].attempts[0].statusText").value("handler succeeded"));

        // 2. Query single event by PayPal Event ID
        mockMvc.perform(get("/api/events/WH-INBOX-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paypalEventId").value("WH-INBOX-1"))
                .andExpect(jsonPath("$.status").value("Processed"))
                .andExpect(jsonPath("$.rawPayload").isNotEmpty());

        // 3. Stats query
        mockMvc.perform(get("/api/events/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(1))
                .andExpect(jsonPath("$.verified").value(1))
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.failedOrDuplicate").value(0));
    }

    @Test
    @DisplayName("8. Replay event via POST /api/events/{id}/replay")
    void shouldReplayEventViaInboxReplayEndpoint() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);

        shopOrderService.createOrder("ORDER-REPLAY-API", "PP-ORD-REPLAY-API", Money.parse("99.00", "USD"), "Frank Replay");
        String payload = sampleCaptureCompletedPayload("WH-REPLAY-API", "CAP-REPLAY-API", "ORDER-REPLAY-API", "99.00");

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .header("paypal-auth-algo", "SHA256withRSA")
                .header("paypal-cert-url", "https://api.sandbox.paypal.com/cert.pem")
                .header("paypal-transmission-id", "TX-REPLAY-API")
                .header("paypal-transmission-sig", "c2ln")
                .header("paypal-transmission-time", "2026-11-07T10:02:41Z")
                .content(payload))
                .andExpect(status().isOk());

        // Replay via endpoint
        mockMvc.perform(post("/api/events/WH-REPLAY-API/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Processed"))
                .andExpect(jsonPath("$.paypalEventId").value("WH-REPLAY-API"));

        ShopOrder order = shopOrderService.findOrderById("ORDER-REPLAY-API").orElseThrow();
        assertThat(order.status()).isEqualTo("PAID");
    }
}
