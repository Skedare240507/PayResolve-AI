package com.payresolve.actions;

import com.payresolve.actions.dto.ProposedActionDto;
import com.payresolve.audit.AuditService;
import com.payresolve.cases.CaseRecord;
import com.payresolve.cases.CaseService;
import com.payresolve.cases.CaseStatus;
import com.payresolve.cases.dto.PagedResponse;
import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.shop.ShopOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service orchestrating human-in-the-loop action proposals, approvals, rejections,
 * idempotent execution, and append-only audit logging.
 */
@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    private final ProposedActionRepository actionRepository;
    private final CaseService caseService;
    private final ShopOrderService shopOrderService;
    private final AuditService auditService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ActionService(
        ProposedActionRepository actionRepository,
        CaseService caseService,
        ShopOrderService shopOrderService,
        AuditService auditService,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        this.actionRepository = actionRepository;
        this.caseService = caseService;
        this.shopOrderService = shopOrderService;
        this.auditService = auditService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Proposes an action for a case. Idempotent based on idempotencyKey.
     */
    @Transactional
    public ProposedActionDto proposeAction(
        String caseId,
        ActionType type,
        Map<String, Object> payload,
        String idempotencyKey,
        String proposedBy
    ) {
        // Enforce valid case
        CaseRecord caseRecord = caseService.getCaseRecordById(caseId);

        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey.trim()
            : caseId + "-" + type.name() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // Idempotency check: if action with this key exists, return it
        Optional<ProposedActionRecord> existing = actionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            log.info("Action with idempotencyKey {} already exists. Returning existing.", key);
            return toDto(existing.get());
        }

        String payloadJson = "{}";
        if (payload != null && !payload.isEmpty()) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                log.warn("Failed to serialize action payload: {}", e.getMessage());
            }
        }

        Instant now = clock.instant();
        ProposedActionRecord newRecord = new ProposedActionRecord(
            null,
            caseId,
            type,
            payloadJson,
            key,
            ActionStatus.AWAITING_APPROVAL,
            proposedBy != null ? proposedBy : "AI",
            null,
            null,
            now
        );

        Long insertedId = actionRepository.insert(newRecord);
        log.info("Created proposed action id={} for case {}", insertedId, caseId);

        // If case is OPEN or INVESTIGATING, transition to ACTION_PROPOSED
        if (caseRecord.status() == CaseStatus.OPEN || caseRecord.status() == CaseStatus.INVESTIGATING) {
            caseService.updateCaseStatus(caseId, CaseStatus.ACTION_PROPOSED);
        }

        auditService.record(
            proposedBy != null ? proposedBy : "AI",
            "ACTION_PROPOSED",
            "CASE",
            caseId,
            Map.of("actionId", insertedId, "type", type.name(), "idempotencyKey", key)
        );

        ProposedActionRecord persisted = actionRepository.findById(insertedId)
            .orElse(newRecord);
        return toDto(persisted);
    }

    /**
     * Approves and executes a proposed action.
     * Idempotent: repeated approvals return the existing approved state without re-executing.
     */
    @Transactional
    public ProposedActionDto approveAction(Long actionId, String decidedBy) {
        ProposedActionRecord action = actionRepository.findById(actionId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposed action not found: " + actionId));

        // Idempotent guard: if already approved or executed, return immediately
        if (action.status() == ActionStatus.APPROVED || action.status() == ActionStatus.EXECUTED) {
            log.info("Action {} is already approved/executed. Idempotently returning existing record.", actionId);
            return toDto(action);
        }

        String actor = decidedBy != null ? decidedBy : "operator";
        Instant now = clock.instant();

        // Execute domain side-effect based on action type
        CaseRecord caseRecord = caseService.getCaseRecordById(action.caseId());

        switch (action.type()) {
            case SYNC_ORDER_STATUS -> {
                if (caseRecord.shopOrderId() != null) {
                    shopOrderService.markOrderPaid(caseRecord.shopOrderId());
                    auditService.record(actor, "ORDER_STATUS_SYNCED", "SHOP_ORDER", caseRecord.shopOrderId(),
                        Map.of("newStatus", "PAID", "actionId", actionId));
                }
                caseService.updateCaseStatus(caseRecord.id(), CaseStatus.RESOLVED);
            }
            case HOLD_REFUND -> {
                // Sets hold flag on refund/case under strict money safety (no funds move)
                auditService.record(actor, "REFUND_HELD", "CASE", caseRecord.id(),
                    Map.of("actionId", actionId, "flag", "HOLD_REFUND", "status", "HELD"));
                caseService.updateCaseStatus(caseRecord.id(), CaseStatus.RESOLVED);
            }
            case REQUEST_REVIEW -> {
                auditService.record(actor, "REVIEW_REQUESTED", "CASE", caseRecord.id(),
                    Map.of("actionId", actionId, "note", "Manual accounting review requested by operator"));
            }
            case NONE -> {
                caseService.updateCaseStatus(caseRecord.id(), CaseStatus.RESOLVED);
            }
        }

        actionRepository.updateDecision(actionId, ActionStatus.APPROVED, actor, now);

        auditService.record(
            actor,
            "ACTION_APPROVED",
            "ACTION",
            actionId.toString(),
            Map.of("caseId", action.caseId(), "type", action.type().name(), "idempotencyKey", action.idempotencyKey())
        );

        ProposedActionRecord updated = actionRepository.findById(actionId)
            .orElseThrow(() -> new IllegalStateException("Failed to reload action: " + actionId));

        return toDto(updated);
    }

    /**
     * Rejects a proposed action. Idempotent.
     */
    @Transactional
    public ProposedActionDto rejectAction(Long actionId, String decidedBy) {
        ProposedActionRecord action = actionRepository.findById(actionId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposed action not found: " + actionId));

        if (action.status() == ActionStatus.REJECTED) {
            log.info("Action {} is already rejected. Idempotently returning existing record.", actionId);
            return toDto(action);
        }

        String actor = decidedBy != null ? decidedBy : "operator";
        Instant now = clock.instant();

        actionRepository.updateDecision(actionId, ActionStatus.REJECTED, actor, now);

        auditService.record(
            actor,
            "ACTION_REJECTED",
            "ACTION",
            actionId.toString(),
            Map.of("caseId", action.caseId(), "type", action.type().name())
        );

        ProposedActionRecord updated = actionRepository.findById(actionId)
            .orElseThrow(() -> new IllegalStateException("Failed to reload action: " + actionId));

        return toDto(updated);
    }

    public List<ProposedActionDto> getActionsByCaseId(String caseId) {
        return actionRepository.findByCaseId(caseId).stream()
            .map(this::toDto)
            .toList();
    }

    public ProposedActionDto getActionById(Long id) {
        ProposedActionRecord action = actionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Proposed action not found: " + id));
        return toDto(action);
    }

    public PagedResponse<ProposedActionDto> listActions(String status, int page, int size) {
        int offset = page * size;
        List<ProposedActionRecord> records = actionRepository.findAll(status, size, offset);
        long total = actionRepository.countAll(status);

        List<ProposedActionDto> dtos = records.stream()
            .map(this::toDto)
            .toList();

        return PagedResponse.of(dtos, page, size, total);
    }

    @SuppressWarnings("unchecked")
    private ProposedActionDto toDto(ProposedActionRecord record) {
        Map<String, Object> payload = Map.of();
        if (record.payloadJson() != null && !record.payloadJson().isBlank()) {
            try {
                payload = objectMapper.readValue(record.payloadJson(), Map.class);
            } catch (Exception e) {
                payload = Map.of("raw", record.payloadJson());
            }
        }
        return ProposedActionDto.fromRecord(record, payload);
    }
}

