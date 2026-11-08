package com.payresolve.investigator;

import com.payresolve.cases.CaseRecord;
import com.payresolve.cases.CaseSeverity;
import com.payresolve.cases.CaseService;
import com.payresolve.cases.CaseStatus;
import com.payresolve.cases.CaseType;
import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.evidence.CaseEvidenceRepository;
import com.payresolve.evidence.EvidenceService;
import com.payresolve.ingest.WebhookEventRepository;
import com.payresolve.investigator.model.*;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestigatorServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private EvidenceService evidenceService;

    @Mock
    private CaseEvidenceRepository caseEvidenceRepository;

    @Mock
    private ShopOrderService shopOrderService;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private InvestigationRepository investigationRepository;

    @Mock
    private InvestigationStepRepository stepRepository;

    @Mock
    private InvestigatorTools investigatorTools;

    private final InvestigationVerifier verifier = new InvestigationVerifier();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private InvestigatorService service;

    @BeforeEach
    void setUp() {
        service = new InvestigatorService(
            caseService,
            evidenceService,
            caseEvidenceRepository,
            shopOrderService,
            webhookEventRepository,
            investigationRepository,
            stepRepository,
            investigatorTools,
            verifier,
            clock,
            objectMapper
        );
        service.init();
    }

    @Test
    @DisplayName("Should execute investigation using cached/rule-based fallback when model is unavailable")
    void shouldExecuteInvestigationWithFallback() {
        CaseRecord caseRecord = new CaseRecord(
            "PR-0142",
            CaseType.PAID_NOT_MARKED,
            CaseStatus.OPEN,
            CaseSeverity.HIGH,
            "PP-10482",
            "ORD-10482",
            8450L,
            "USD",
            clock.instant().minusSeconds(120),
            clock.instant().minusSeconds(120),
            null,
            "Customer says paid, system says unpaid."
        );

        when(caseService.getCaseRecordById("PR-0142")).thenReturn(caseRecord);
        when(caseEvidenceRepository.findByCaseId("PR-0142")).thenReturn(List.of());
        when(shopOrderService.findOrderById("ORD-10482")).thenReturn(Optional.empty());
        when(webhookEventRepository.findAll(anyInt(), anyInt())).thenReturn(List.of());

        InvestigationResponseDto response = service.investigateCase("PR-0142");

        assertThat(response).isNotNull();
        assertThat(response.caseId()).isEqualTo("PR-0142");
        assertThat(response.report()).isNotNull();
        assertThat(response.report().recommendedAction().type()).isEqualTo("SYNC_ORDER_STATUS");

        // Verify status transitions
        verify(caseService).updateCaseStatus("PR-0142", CaseStatus.INVESTIGATING);
        verify(caseService).updateCaseStatus("PR-0142", CaseStatus.ACTION_PROPOSED);

        // Verify persistence
        ArgumentCaptor<InvestigationRecord> captor = ArgumentCaptor.forClass(InvestigationRecord.class);
        verify(investigationRepository).insert(captor.capture());
        assertThat(captor.getValue().caseId()).isEqualTo("PR-0142");
    }

    @Test
    @DisplayName("Should retrieve investigation by ID and include auditable steps")
    void shouldRetrieveInvestigationById() {
        String invId = "INV-PR-0142-12345";
        InvestigationRecord record = new InvestigationRecord(
            invId,
            "PR-0142",
            InvestigationStatus.CAUSE_FOUND,
            """
            {
              "status": "CAUSE_FOUND",
              "confirmed": [{"statement": "Capture completed", "evidenceIds": ["PP-10482"]}],
              "conflicts": [],
              "missing": [],
              "recommendedAction": {"type": "SYNC_ORDER_STATUS", "rationale": "Sync order", "evidenceIds": ["ORD-10482"]},
              "degraded": false,
              "explanation": "Verified cause"
            }
            """,
            "gemini-2.0-flash",
            clock.instant().minusSeconds(10),
            clock.instant()
        );

        InvestigationStepRecord step = new InvestigationStepRecord(
            1L,
            invId,
            "getPaypalOrder",
            "{\"paypalOrderId\":\"PP-10482\"}",
            "PayPal order APPROVED",
            clock.instant().minusSeconds(8),
            25L
        );

        when(investigationRepository.findById(invId)).thenReturn(Optional.of(record));
        when(stepRepository.findByInvestigationId(invId)).thenReturn(List.of(step));

        InvestigationResponseDto result = service.getInvestigationById(invId);

        assertThat(result.id()).isEqualTo(invId);
        assertThat(result.caseId()).isEqualTo("PR-0142");
        assertThat(result.status()).isEqualTo("CAUSE_FOUND");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).tool()).isEqualTo("getPaypalOrder");
        assertThat(result.steps().get(0).latencyMs()).isEqualTo(25L);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for unknown investigation ID")
    void shouldThrowForUnknownInvestigationId() {
        when(investigationRepository.findById("UNKNOWN-ID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInvestigationById("UNKNOWN-ID"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("UNKNOWN-ID");
    }
}

