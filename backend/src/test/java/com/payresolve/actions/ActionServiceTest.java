package com.payresolve.actions;

import com.payresolve.actions.dto.ProposedActionDto;
import com.payresolve.audit.AuditService;
import com.payresolve.cases.CaseRecord;
import com.payresolve.cases.CaseSeverity;
import com.payresolve.cases.CaseService;
import com.payresolve.cases.CaseStatus;
import com.payresolve.cases.CaseType;
import com.payresolve.common.ResourceNotFoundException;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    @Mock
    private ProposedActionRepository actionRepository;

    @Mock
    private CaseService caseService;

    @Mock
    private ShopOrderService shopOrderService;

    @Mock
    private AuditService auditService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ActionService actionService;
    private CaseRecord caseRecord;

    @BeforeEach
    void setUp() {
        actionService = new ActionService(
            actionRepository,
            caseService,
            shopOrderService,
            auditService,
            clock,
            objectMapper
        );

        caseRecord = new CaseRecord(
            "PR-0142",
            CaseType.PAID_NOT_MARKED,
            CaseStatus.ACTION_PROPOSED,
            CaseSeverity.HIGH,
            "PP-10482",
            "ORD-10482",
            8450L,
            "USD",
            clock.instant().minusSeconds(100),
            clock.instant().minusSeconds(100),
            null,
            "Payment captured but shop order unconfirmed."
        );
    }

    @Test
    @DisplayName("Should propose action and append to audit log with unique idempotency key")
    void shouldProposeAction() {
        when(caseService.getCaseRecordById("PR-0142")).thenReturn(caseRecord);
        when(actionRepository.findByIdempotencyKey("KEY-123")).thenReturn(Optional.empty());
        when(actionRepository.insert(any())).thenReturn(101L);

        ProposedActionRecord persisted = new ProposedActionRecord(
            101L, "PR-0142", ActionType.SYNC_ORDER_STATUS, "{}", "KEY-123",
            ActionStatus.AWAITING_APPROVAL, "AI", null, null, clock.instant()
        );
        when(actionRepository.findById(101L)).thenReturn(Optional.of(persisted));

        ProposedActionDto dto = actionService.proposeAction(
            "PR-0142",
            ActionType.SYNC_ORDER_STATUS,
            Map.of("note", "Reconcile order"),
            "KEY-123",
            "AI"
        );

        assertThat(dto.id()).isEqualTo(101L);
        assertThat(dto.type()).isEqualTo("SYNC_ORDER_STATUS");
        assertThat(dto.status()).isEqualTo("AWAITING_APPROVAL");

        verify(auditService).record(eq("AI"), eq("ACTION_PROPOSED"), eq("CASE"), eq("PR-0142"), any());
    }

    @Test
    @DisplayName("Should execute SYNC_ORDER_STATUS, mark shop order paid, resolve case, and audit")
    void shouldApproveSyncOrderStatus() {
        ProposedActionRecord existing = new ProposedActionRecord(
            101L, "PR-0142", ActionType.SYNC_ORDER_STATUS, "{}", "KEY-123",
            ActionStatus.AWAITING_APPROVAL, "AI", null, null, clock.instant().minusSeconds(10)
        );

        ProposedActionRecord approved = new ProposedActionRecord(
            101L, "PR-0142", ActionType.SYNC_ORDER_STATUS, "{}", "KEY-123",
            ActionStatus.APPROVED, "AI", "operator", clock.instant(), clock.instant().minusSeconds(10)
        );

        when(actionRepository.findById(101L)).thenReturn(Optional.of(existing), Optional.of(approved));
        when(caseService.getCaseRecordById("PR-0142")).thenReturn(caseRecord);

        ProposedActionDto dto = actionService.approveAction(101L, "operator");

        assertThat(dto.status()).isEqualTo("APPROVED");
        assertThat(dto.decidedBy()).isEqualTo("operator");

        // Verify side-effects
        verify(shopOrderService).markOrderPaid("ORD-10482");
        verify(caseService).updateCaseStatus("PR-0142", CaseStatus.RESOLVED);
        verify(actionRepository).updateDecision(eq(101L), eq(ActionStatus.APPROVED), eq("operator"), any());
        verify(auditService).record(eq("operator"), eq("ORDER_STATUS_SYNCED"), eq("SHOP_ORDER"), eq("ORD-10482"), any());
        verify(auditService).record(eq("operator"), eq("ACTION_APPROVED"), eq("ACTION"), eq("101"), any());
    }

    @Test
    @DisplayName("Double approve is idempotent: re-approving returns existing state without duplicate effect")
    void doubleApproveIsIdempotent() {
        ProposedActionRecord alreadyApproved = new ProposedActionRecord(
            101L, "PR-0142", ActionType.SYNC_ORDER_STATUS, "{}", "KEY-123",
            ActionStatus.APPROVED, "AI", "operator", clock.instant().minusSeconds(5), clock.instant().minusSeconds(10)
        );

        when(actionRepository.findById(101L)).thenReturn(Optional.of(alreadyApproved));

        ProposedActionDto dto = actionService.approveAction(101L, "operator");

        assertThat(dto.status()).isEqualTo("APPROVED");

        // Side-effects must NOT be executed again
        verify(shopOrderService, never()).markOrderPaid(any());
        verify(caseService, never()).updateCaseStatus(any(), any());
        verify(actionRepository, never()).updateDecision(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rejecting an action updates status to REJECTED and records audit entry")
    void shouldRejectAction() {
        ProposedActionRecord existing = new ProposedActionRecord(
            102L, "PR-0142", ActionType.REQUEST_REVIEW, "{}", "KEY-456",
            ActionStatus.AWAITING_APPROVAL, "AI", null, null, clock.instant().minusSeconds(10)
        );

        ProposedActionRecord rejected = new ProposedActionRecord(
            102L, "PR-0142", ActionType.REQUEST_REVIEW, "{}", "KEY-456",
            ActionStatus.REJECTED, "AI", "operator", clock.instant(), clock.instant().minusSeconds(10)
        );

        when(actionRepository.findById(102L)).thenReturn(Optional.of(existing), Optional.of(rejected));

        ProposedActionDto dto = actionService.rejectAction(102L, "operator");

        assertThat(dto.status()).isEqualTo("REJECTED");
        verify(actionRepository).updateDecision(eq(102L), eq(ActionStatus.REJECTED), eq("operator"), any());
        verify(auditService).record(eq("operator"), eq("ACTION_REJECTED"), eq("ACTION"), eq("102"), any());
    }
}

