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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

    @Autowired
    private com.payresolve.common.RateLimitService rateLimitService;

    @Autowired
    private com.payresolve.cases.CaseDetectionService caseDetectionService;

    @MockitoBean
    private PayPalWebhookVerificationClient verificationClient;

    @BeforeEach
    void setUp() {
        faultSwitches.reset();
        rateLimitService.resetAll();
        jdbcClient.sql("DELETE FROM case_evidence").update();
        jdbcClient.sql("DELETE FROM investigation_steps").update();
        jdbcClient.sql("DELETE FROM investigations").update();
        jdbcClient.sql("DELETE FROM proposed_actions").update();
        jdbcClient.sql("DELETE FROM cases").update();
        jdbcClient.sql("DELETE FROM payments").update();
        jdbcClient.sql("DELETE FROM shop_orders").update();
        jdbcClient.sql("DELETE FROM rejected_webhooks").update();
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
    @DisplayName("2. Unverified webhook goes into rejected_webhooks only, never enters webhook_events or creates pending work")
    void shouldQuarantineUnverifiedWebhookInRejectedTable() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(false);

        shopOrderService.createOrder("ORDER-102", "PP-ORD-102", Money.parse("25.00", "USD"), "Bob Fake");
        String payload = sampleCaptureCompletedPayload("WH-EVT-BAD", "CAP-BAD", "ORDER-102", "25.00");

        mockMvc.perform(post("/api/webhooks/paypal")
                .with(req -> { req.setRemoteAddr("198.51.100.120"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.verified").value(false));

        // Main inbox webhook_events has ZERO rows
        assertThat(repository.findByPaypalEventId("WH-EVT-BAD")).isEmpty();

        // Quarantined in rejected_webhooks with SHA-256 and IP
        Integer rejectedCount = jdbcClient.sql("SELECT COUNT(*) FROM rejected_webhooks WHERE paypal_event_id = 'WH-EVT-BAD' AND ip = '198.51.100.120'")
                .query(Integer.class).single();
        assertThat(rejectedCount).isEqualTo(1);

        // Worker skips and order stays PENDING
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
        mockMvc.perform(get("/api/events").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("WH-INBOX-1"))
                .andExpect(jsonPath("$[0].type").value("PAYMENT.CAPTURE.COMPLETED"))
                .andExpect(jsonPath("$[0].signature").value("Verified"))
                .andExpect(jsonPath("$[0].status").value("Processed"))
                .andExpect(jsonPath("$[0].attempts[0].label").value("Attempt 1"))
                .andExpect(jsonPath("$[0].attempts[0].statusText").value("handler succeeded"));

        // 2. Query single event by PayPal Event ID
        mockMvc.perform(get("/api/events/WH-INBOX-1").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paypalEventId").value("WH-INBOX-1"))
                .andExpect(jsonPath("$.status").value("Processed"))
                .andExpect(jsonPath("$.rawPayload").isNotEmpty());

        // 3. Stats query
        mockMvc.perform(get("/api/events/stats").with(user("viewer").roles("VIEWER")))
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
        mockMvc.perform(post("/api/events/WH-REPLAY-API/replay")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Processed"))
                .andExpect(jsonPath("$.paypalEventId").value("WH-REPLAY-API"));

        ShopOrder order = shopOrderService.findOrderById("ORDER-REPLAY-API").orElseThrow();
        assertThat(order.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("9. Retried single verified delivery (attempts = 2) creates ZERO open DUPLICATE_EVENT case")
    void retriedSingleDeliveryCreatesNoCase() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);

        shopOrderService.createOrder("ORDER-RETRY-OK", "PP-ORD-RETRY-OK", Money.parse("50.00", "USD"), "Retry Buyer");
        String payload = sampleCaptureCompletedPayload("WH-RETRY-OK", "CAP-RETRY-OK", "ORDER-RETRY-OK", "50.00");

        mockMvc.perform(post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        // Attempt 1 fails due to transient fault switch
        faultSwitches.setFailWebhookHandler(true);
        assertThat(workerService.processBatch(10)).isEqualTo(0);

        // Turn off fault switch, wait past 50ms backoff, attempt 2 succeeds
        faultSwitches.setFailWebhookHandler(false);
        Thread.sleep(75);
        assertThat(workerService.processBatch(10)).isEqualTo(1);

        WebhookEvent event = repository.findByPaypalEventId("WH-RETRY-OK").orElseThrow();
        assertThat(event.attempts()).isEqualTo(2);
        assertThat(event.deliveryCount()).isEqualTo(1);

        Integer openDupCases = jdbcClient.sql("SELECT COUNT(*) FROM cases WHERE type = 'DUPLICATE_EVENT' AND status <> 'RESOLVED'")
                .query(Integer.class).single();
        assertThat(openDupCases).isEqualTo(0);
    }

    @Test
    @DisplayName("10. Two verified deliveries create ZERO cases, delivery_count=2, one payment effect, and count toward duplicates blocked")
    void twoVerifiedDeliveriesCreateZeroCasesAndIncrementDuplicatesBlocked() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);

        shopOrderService.createOrder("ORDER-DUP-INFO", "PP-ORD-DUP-INFO", Money.parse("40.00", "USD"), "Dup Buyer");
        String payload = sampleCaptureCompletedPayload("WH-DUP-INFO", "CAP-DUP-INFO", "ORDER-DUP-INFO", "40.00");

        // Delivery 1 + process
        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        assertThat(workerService.processBatch(10)).isEqualTo(1);

        // Delivery 2 (verified duplicate)
        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        WebhookEvent stored = repository.findByPaypalEventId("WH-DUP-INFO").orElseThrow();
        assertThat(stored.deliveryCount()).isEqualTo(2);

        // Exactly 1 payment record
        Integer paymentCount = jdbcClient.sql("SELECT COUNT(*) FROM payments WHERE paypal_capture_id = 'CAP-DUP-INFO'")
                .query(Integer.class).single();
        assertThat(paymentCount).isEqualTo(1);

        // ZERO cases created (absorbed duplicate)
        Integer caseCount = jdbcClient.sql("SELECT COUNT(*) FROM cases").query(Integer.class).single();
        assertThat(caseCount).isEqualTo(0);

        // Unverified duplicate delivery must NOT count toward duplicatesBlockedToday
        repository.insert("WH-UNVERIFIED-DUP", "PAYMENT.CAPTURE.COMPLETED", "CAP-X", "{}", null, false, "UNVERIFIED");
        jdbcClient.sql("UPDATE webhook_events SET delivery_count = 4 WHERE paypal_event_id = 'WH-UNVERIFIED-DUP'").update();

        // Counts toward duplicatesBlockedToday = SUM(delivery_count - 1) over verified events = (2 - 1) = 1
        mockMvc.perform(get("/api/cases/stats").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(0))
                .andExpect(jsonPath("$.duplicatesBlockedToday").value(1));
    }

    @Test
    @DisplayName("11. Forged unverified delivery with a real event ID changes nothing; unverified placeholder upgrades on verified arrival")
    void forgedUnverifiedDeliveryCannotPoisonExistingEvent() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);
        shopOrderService.createOrder("ORDER-POISON", "PP-ORD-POISON", Money.parse("33.00", "USD"), "Safe Buyer");
        String legitimate = sampleCaptureCompletedPayload("WH-POISON-1", "CAP-POISON-1", "ORDER-POISON", "33.00");

        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(legitimate))
                .andExpect(status().isOk());

        // Now attacker sends an unverified forged payload with the same paypal_event_id WH-POISON-1
        when(verificationClient.verifySignature(any(), any())).thenReturn(false);
        String forged = sampleCaptureCompletedPayload("WH-POISON-1", "CAP-FORGED", "ORDER-POISON", "999.00");
        mockMvc.perform(post("/api/webhooks/paypal")
                .with(req -> { req.setRemoteAddr("198.51.100.200"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));

        WebhookEvent untouched = repository.findByPaypalEventId("WH-POISON-1").orElseThrow();
        assertThat(untouched.verified()).isTrue();
        assertThat(untouched.deliveryCount()).isEqualTo(1);
        assertThat(untouched.rawBody()).contains("CAP-POISON-1").doesNotContain("CAP-FORGED");

        // Verify upgrade-in-place if an unverified row existed directly in webhook_events
        repository.insert("WH-UPGRADE-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-FAKE", "{\"forged\":true}", null, false, "UNVERIFIED");
        String realPayload = sampleCaptureCompletedPayload("WH-UPGRADE-1", "CAP-POISON-1", "ORDER-POISON", "33.00");
        repository.upsert("WH-UPGRADE-1", "PAYMENT.CAPTURE.COMPLETED", "CAP-POISON-1", realPayload, null, true, "PENDING", null, "ORDER-POISON", "PP-ORD-POISON");
        WebhookEvent upgraded = repository.findByPaypalEventId("WH-UPGRADE-1").orElseThrow();
        assertThat(upgraded.verified()).isTrue();
        assertThat(upgraded.status()).isEqualTo("PENDING");
        assertThat(upgraded.deliveryCount()).isEqualTo(1);
        assertThat(upgraded.rawBody()).contains("CAP-POISON-1");
    }

    @Test
    @DisplayName("12. Arrival race: webhook arriving before order is linked re-queues with next_attempt_at without incrementing attempts")
    void arrivalRaceRequeuesWithoutFailureThenApplies() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);
        String payload = sampleCaptureCompletedPayload("WH-RACE-1", "CAP-RACE-1", "ORDER-RACE-1", "75.00");

        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        // Order does not exist yet -> re-queues with next_attempt_at, attempts remains 0, status remains PENDING
        assertThat(workerService.processBatch(10)).isEqualTo(0);
        WebhookEvent waiting = repository.findByPaypalEventId("WH-RACE-1").orElseThrow();
        assertThat(waiting.status()).isEqualTo("PENDING");
        assertThat(waiting.attempts()).isEqualTo(0);
        assertThat(waiting.nextAttemptAt()).isNotNull();

        // Immediate poll before next_attempt_at claims nothing
        assertThat(workerService.processBatch(10)).isEqualTo(0);

        // Order is created/linked and next_attempt_at becomes due
        shopOrderService.createOrder("ORDER-RACE-1", "PP-ORD-RACE-1", Money.parse("75.00", "USD"), "Race Buyer");
        jdbcClient.sql("UPDATE webhook_events SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1' SECOND WHERE paypal_event_id = 'WH-RACE-1'")
                .update();

        assertThat(workerService.processBatch(10)).isEqualTo(1);
        ShopOrder order = shopOrderService.findOrderById("ORDER-RACE-1").orElseThrow();
        assertThat(order.status()).isEqualTo("PAID");
        assertThat(repository.findByPaypalEventId("WH-RACE-1").orElseThrow().attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("13. Normal payment with 1 handler retry creates zero cases")
    void normalPaymentWithHandlerRetryCreatesZeroCases() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);
        shopOrderService.createOrder("ORD-RETRY-1", "PP-RETRY-1", Money.parse("62.50", "USD"), "Retry Buyer");

        // Simulate 1 handler retry (attempts = 2, delivery_count = 1)
        String payload = sampleCaptureCompletedPayload("WH-RETRY-1", "CAP-RETRY-1", "ORD-RETRY-1", "62.50");
        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        jdbcClient.sql("UPDATE webhook_events SET attempts = 1 WHERE paypal_event_id = 'WH-RETRY-1'").update();

        assertThat(workerService.processBatch(10)).isEqualTo(1);
        WebhookEvent processed = repository.findByPaypalEventId("WH-RETRY-1").orElseThrow();
        assertThat(processed.attempts()).isEqualTo(2);
        assertThat(processed.deliveryCount()).isEqualTo(1);

        caseDetectionService.detectForEvent(processed);
        Integer caseCount = jdbcClient.sql("SELECT COUNT(*) FROM cases").query(Integer.class).single();
        assertThat(caseCount).isEqualTo(0);
    }

    @Test
    @DisplayName("14. Two orders with the same amount never get each other's events")
    void twoOrdersWithSameAmountNeverCrossMatchEvents() throws Exception {
        when(verificationClient.verifySignature(any(), any())).thenReturn(true);
        shopOrderService.createOrder("ORD-SAME-A", "PP-SAME-A", Money.parse("62.50", "USD"), "Buyer A");
        shopOrderService.createOrder("ORD-SAME-B", "PP-SAME-B", Money.parse("62.50", "USD"), "Buyer B");

        // Pay only ORD-SAME-A
        String payloadA = sampleCaptureCompletedPayload("WH-SAME-A", "CAP-SAME-A", "ORD-SAME-A", "62.50");
        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(payloadA))
                .andExpect(status().isOk());
        assertThat(workerService.processBatch(10)).isEqualTo(1);

        // Even if ORD-SAME-A's event were delivered twice, ORD-SAME-B must never receive any case or evidence for WH-SAME-A
        mockMvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(payloadA))
                .andExpect(status().isOk());

        caseDetectionService.runDetection();
        Integer casesForB = jdbcClient.sql("SELECT COUNT(*) FROM cases WHERE shop_order_id = 'ORD-SAME-B'")
                .query(Integer.class).single();
        assertThat(casesForB).isEqualTo(0);
    }

    @Test
    @DisplayName("15. Two payment rows for one capture id create exactly one OPEN HIGH DUPLICATE_EVENT case")
    void twoPaymentRowsForOneCaptureCreateOneOpenHighCase() {
        shopOrderService.createOrder("ORD-2PAY", "PP-2PAY", Money.parse("62.50", "USD"), "Double Pay Buyer");
        jdbcClient.sql("UPDATE shop_orders SET status = 'PAID' WHERE id = 'ORD-2PAY'").update();
        jdbcClient.sql("INSERT INTO payments (id, shop_order_id, paypal_capture_id, amount_minor, currency, status) VALUES ('PAY-A', 'ORD-2PAY', 'CAP-2PAY', 6250, 'USD', 'COMPLETED')").update();
        jdbcClient.sql("INSERT INTO payments (id, shop_order_id, paypal_capture_id, amount_minor, currency, status) VALUES ('PAY-B', 'ORD-2PAY', 'CAP-2PAY', 6250, 'USD', 'COMPLETED')").update();

        caseDetectionService.runDetection();

        var row = jdbcClient.sql("SELECT type, status, severity FROM cases WHERE shop_order_id = 'ORD-2PAY'")
                .query((rs, n) -> java.util.List.of(rs.getString("type"), rs.getString("status"), rs.getString("severity")))
                .single();
        assertThat(row.get(0)).isEqualTo("DUPLICATE_EVENT");
        assertThat(row.get(1)).isEqualTo("OPEN");
        assertThat(row.get(2)).isEqualTo("HIGH");

        // Re-running detection is idempotent (still 1 case)
        caseDetectionService.runDetection();
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM cases WHERE shop_order_id = 'ORD-2PAY'")
                .query(Integer.class).single();
        assertThat(count).isEqualTo(1);
    }
}
