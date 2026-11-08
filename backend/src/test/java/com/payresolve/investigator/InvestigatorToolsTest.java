package com.payresolve.investigator;

import com.payresolve.evidence.CaseEvidenceRepository;
import com.payresolve.ingest.WebhookEventRepository;
import com.payresolve.investigator.model.InvestigationStepRecord;
import com.payresolve.paypal.PayPalDisputesClient;
import com.payresolve.paypal.PayPalOrdersClient;
import com.payresolve.paypal.PayPalPaymentsClient;
import com.payresolve.paypal.model.CaptureDetails;
import com.payresolve.paypal.model.DisputeDetail;
import com.payresolve.paypal.model.PayPalOrder;
import com.payresolve.shop.ShopOrder;
import com.payresolve.shop.ShopOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestigatorToolsTest {

    @Mock
    private PayPalOrdersClient payPalOrdersClient;

    @Mock
    private PayPalPaymentsClient payPalPaymentsClient;

    @Mock
    private PayPalDisputesClient payPalDisputesClient;

    @Mock
    private ShopOrderService shopOrderService;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private CaseEvidenceRepository caseEvidenceRepository;

    @Mock
    private InvestigationStepRepository stepRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);

    private InvestigatorTools tools;

    @BeforeEach
    void setUp() {
        tools = new InvestigatorTools(
            payPalOrdersClient,
            payPalPaymentsClient,
            payPalDisputesClient,
            shopOrderService,
            webhookEventRepository,
            caseEvidenceRepository,
            stepRepository,
            objectMapper,
            clock
        );
    }

    @Test
    @DisplayName("Tool execution records step into investigation_steps with latency")
    void shouldRecordStepWithLatency() {
        String invId = "INV-TEST-001";
        InvestigationContext.setCurrentInvestigationId(invId);
        tools.resetStepCounter(invId);

        try {
            PayPalOrder mockOrder = new PayPalOrder("PP-10482", "APPROVED", "CAPTURE", List.of(), List.of());
            when(payPalOrdersClient.getOrder("PP-10482")).thenReturn(mockOrder);

            String result = tools.getPaypalOrder("PP-10482");

            assertThat(result).contains("PP-10482");
            assertThat(result).contains("APPROVED");

            ArgumentCaptor<InvestigationStepRecord> captor = ArgumentCaptor.forClass(InvestigationStepRecord.class);
            verify(stepRepository, times(1)).insert(captor.capture());

            InvestigationStepRecord step = captor.getValue();
            assertThat(step.investigationId()).isEqualTo(invId);
            assertThat(step.tool()).isEqualTo("getPaypalOrder");
            assertThat(step.argsJson()).contains("PP-10482");
            assertThat(step.resultDigest()).contains("APPROVED");
            assertThat(step.ms()).isGreaterThanOrEqualTo(0);
        } finally {
            InvestigationContext.clear();
        }
    }

    @Test
    @DisplayName("Tool enforces step cap of 10 and stops executing queries")
    void shouldEnforceStepCap() {
        String invId = "INV-STEP-CAP-001";
        InvestigationContext.setCurrentInvestigationId(invId);
        tools.resetStepCounter(invId);

        try {
            when(shopOrderService.findOrderById(any())).thenReturn(Optional.of(
                new ShopOrder("ORD-1", "PP-1", "PAID", 1000L, "USD", "Buyer", Instant.now(), Instant.now())
            ));

            // Execute 10 times (up to cap)
            for (int i = 0; i < 10; i++) {
                String res = tools.getShopOrder("ORD-1");
                assertThat(res).doesNotContain("STEP_CAP_REACHED");
            }

            // 11th call must return step cap reached
            String cappedResult = tools.getShopOrder("ORD-1");
            assertThat(cappedResult).contains("STEP_CAP_REACHED");
        } finally {
            InvestigationContext.clear();
        }
    }

    @Test
    @DisplayName("Tool returns graceful not-found JSON when PayPal order does not exist")
    void shouldReturnGracefulNotFound() {
        String invId = "INV-NOT-FOUND-001";
        InvestigationContext.setCurrentInvestigationId(invId);
        tools.resetStepCounter(invId);

        try {
            when(payPalOrdersClient.getOrder("PP-UNKNOWN")).thenThrow(new RuntimeException("404 Not Found"));
            when(shopOrderService.findOrderByPayPalOrderId("PP-UNKNOWN")).thenReturn(Optional.empty());

            String result = tools.getPaypalOrder("PP-UNKNOWN");

            assertThat(result).contains("NOT_FOUND");
            assertThat(result).contains("PP-UNKNOWN");

            verify(stepRepository, times(1)).insert(any());
        } finally {
            InvestigationContext.clear();
        }
    }
}

