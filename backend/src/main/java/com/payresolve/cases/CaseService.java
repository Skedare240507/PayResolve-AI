package com.payresolve.cases;

import com.payresolve.cases.dto.CaseDetailsDto;
import com.payresolve.cases.dto.CaseDto;
import com.payresolve.cases.dto.PagedResponse;
import com.payresolve.common.Money;
import com.payresolve.shop.ShopOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service orchestrating case lifecycle management, persistence, retrieval, and demo seeding.
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final CaseRepository caseRepository;
    private final ShopOrderService shopOrderService;
    private final Clock clock;

    public CaseService(CaseRepository caseRepository, ShopOrderService shopOrderService, Clock clock) {
        this.caseRepository = caseRepository;
        this.shopOrderService = shopOrderService;
        this.clock = clock;
    }

    /**
     * Upserts a case from a detector finding.
     * Enforces the invariant: Exactly one case per (type, paypalOrderId); reruns update rather than duplicate.
     * For ORPHAN_CAPTURE or any case where paypal_order_id would be NULL, stores 'capture:' + captureId.
     *
     * @param finding discrepancy finding from pure detector
     * @return persisted or updated CaseRecord
     */
    @Transactional
    public CaseRecord upsertCase(DetectionFinding finding) {
        String paypalOrderId = finding.paypalOrderId();
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            if (finding.type() == CaseType.ORPHAN_CAPTURE) {
                String capId = finding.details().containsKey("captureId")
                    ? String.valueOf(finding.details().get("captureId"))
                    : "unknown";
                paypalOrderId = "capture:" + capId;
            } else if (finding.shopOrderId() != null) {
                paypalOrderId = finding.shopOrderId();
            } else {
                paypalOrderId = "capture:unknown";
            }
        }

        Optional<CaseRecord> existingOpt = caseRepository.findByTypeAndPaypalOrderId(finding.type(), paypalOrderId);
        Instant now = clock.instant();

        Long amountMinor = null;
        String currency = "USD";
        if (finding.discrepancyAmount() != null) {
            amountMinor = finding.discrepancyAmount().amountMinor();
            currency = finding.discrepancyAmount().currency();
        } else if (finding.orderAmount() != null) {
            amountMinor = finding.orderAmount().amountMinor();
            currency = finding.orderAmount().currency();
        }

        if (existingOpt.isPresent()) {
            CaseRecord existing = existingOpt.get();
            log.info("Rerun update for existing case: id={}, type={}, order={}",
                existing.id(), existing.type(), paypalOrderId);

            CaseRecord updated = new CaseRecord(
                existing.id(),
                existing.type(),
                existing.status(), // Keep existing lifecycle status
                finding.severity(),
                paypalOrderId,
                finding.shopOrderId() != null ? finding.shopOrderId() : existing.shopOrderId(),
                amountMinor != null ? amountMinor : existing.amountMinor(),
                currency,
                existing.openedAt(),
                now,
                existing.resolvedAt(),
                finding.summary()
            );

            caseRepository.update(updated);
            return updated;
        } else {
            String newId = generateNextCaseId();
            CaseRecord newCase = new CaseRecord(
                newId,
                finding.type(),
                CaseStatus.OPEN,
                finding.severity(),
                paypalOrderId,
                finding.shopOrderId(),
                amountMinor,
                currency,
                now,
                now,
                null,
                finding.summary()
            );

            caseRepository.insert(newCase);
            return newCase;
        }
    }

    public CaseRecord getCaseRecordById(String id) {
        return caseRepository.findById(id)
            .orElseThrow(() -> new com.payresolve.common.ResourceNotFoundException("Case not found: " + id));
    }

    public CaseDetailsDto getCaseDetailsById(String id) {
        CaseRecord record = getCaseRecordById(id);
        return CaseDetailsDto.fromRecord(record);
    }

    public PagedResponse<CaseDto> getCases(String status, String severity, String type, String search, int page, int size) {
        int offset = page * size;
        List<CaseRecord> records = caseRepository.findAll(status, severity, type, search, size, offset);
        long total = caseRepository.countAll(status, severity, type, search);

        Instant now = clock.instant();
        List<CaseDto> dtos = records.stream()
            .map(r -> CaseDto.fromRecord(r, now))
            .toList();

        return PagedResponse.of(dtos, page, size, total);
    }

    @Transactional
    public CaseDetailsDto updateCaseStatus(String id, CaseStatus newStatus) {
        CaseRecord existing = getCaseRecordById(id);
        Instant resolvedAt = newStatus.isTerminal() ? clock.instant() : null;

        caseRepository.updateStatus(id, newStatus, resolvedAt);
        log.info("Transitioned case {} status from {} to {}", id, existing.status(), newStatus);

        CaseRecord updated = caseRepository.findById(id).orElseThrow();
        return CaseDetailsDto.fromRecord(updated);
    }

    /**
     * Seeds the canonical demo cases for hackathon judge presentations.
     * Preserves existing case ids PR-0142, PR-0141, PR-0140, PR-0138, PR-0137, PR-0135.
     */
    @Transactional
    public List<CaseDto> seedDemoCases() {
        Instant now = clock.instant();

        ensurePrerequisiteShopOrder("ORD-10482", "PP-10482", Money.parse("84.50", "USD"), "AWAITING_CONFIRMATION", "Sarah Connor");
        ensurePrerequisiteShopOrder("ORD-10481", "PP-10481", Money.parse("24.00", "USD"), "AWAITING_CONFIRMATION", "David Miller");
        ensurePrerequisiteShopOrder("ORD-10479", "PP-10479", Money.parse("34.00", "USD"), "PAID", "Altered Buyer");
        ensurePrerequisiteShopOrder("ORD-10477", "PP-10477", Money.parse("12.00", "USD"), "PAID", "Duplicate Buyer");
        ensurePrerequisiteShopOrder("ORD-10470", "PP-10470", Money.parse("38.50", "USD"), "PAID", "Charlie Refund");
        ensurePrerequisiteShopOrder("ORD-10466", "PP-10466", Money.parse("56.20", "USD"), "PAID", "Frank Reconciled");

        List<CaseRecord> seedSpecs = List.of(
            new CaseRecord(
                "PR-0142",
                CaseType.PAID_NOT_MARKED,
                CaseStatus.OPEN,
                CaseSeverity.HIGH,
                "PP-10482",
                "ORD-10482",
                8450L,
                "USD",
                now.minusSeconds(120), // 2 min ago
                now.minusSeconds(120),
                null,
                "Payment captured on PayPal ($84.50) but shop order ORD-10482 remains AWAITING_CONFIRMATION. Webhook handler dropped or failed."
            ),
            new CaseRecord(
                "PR-0141",
                CaseType.AMOUNT_MISMATCH,
                CaseStatus.OPEN,
                CaseSeverity.HIGH,
                "PP-10481",
                "ORD-10481",
                2400L,
                "USD",
                now.minusSeconds(900), // 15 min ago
                now.minusSeconds(900),
                null,
                "PayPal capture CAP-10481 ($24.00) completed. Shop order ORD-10481 not transitioned to PAID."
            ),
            new CaseRecord(
                "PR-0140",
                CaseType.DUPLICATE_EVENT,
                CaseStatus.OPEN,
                CaseSeverity.MEDIUM,
                "PP-10477",
                "ORD-10477",
                1200L,
                "USD",
                now.minusSeconds(1800), // 30 min ago
                now.minusSeconds(1800),
                null,
                "Duplicate delivery detected for webhook event WH-EVT-10477 (delivered 2 times). Business effect executed once idempotently."
            ),
            new CaseRecord(
                "PR-0138",
                CaseType.REFUND_MISMATCH,
                CaseStatus.OPEN,
                CaseSeverity.MEDIUM,
                "PP-10470",
                "ORD-10470",
                3850L,
                "USD",
                now.minusSeconds(3600), // 1 h ago
                now.minusSeconds(3600),
                null,
                "PayPal refund REF-10470 ($38.50) has no matching shop refund record."
            ),
            new CaseRecord(
                "PR-0137",
                CaseType.ORPHAN_CAPTURE,
                CaseStatus.OPEN,
                CaseSeverity.MEDIUM,
                "capture:CAP-ORPHAN-0137",
                null,
                12000L,
                "USD",
                now.minusSeconds(14400), // 4 h ago
                now.minusSeconds(14400),
                null,
                "Orphan capture: PayPal capture CAP-ORPHAN-0137 ($120.00) has no matching merchant shop order."
            ),
            new CaseRecord(
                "PR-0135",
                CaseType.PAID_NOT_MARKED,
                CaseStatus.RESOLVED,
                CaseSeverity.LOW,
                "PP-10466",
                "ORD-10466",
                5620L,
                "USD",
                now.minusSeconds(7200), // 2 h ago
                now.minusSeconds(7200),
                now.minusSeconds(3600),
                "PayPal capture reconciled and shop order ORD-10466 synced to PAID."
            )
        );

        List<CaseDto> result = new ArrayList<>();
        for (CaseRecord spec : seedSpecs) {
            Optional<CaseRecord> existing = caseRepository.findById(spec.id());
            if (existing.isPresent()) {
                caseRepository.update(spec);
            } else {
                caseRepository.insert(spec);
            }
            result.add(CaseDto.fromRecord(spec, now));
        }

        return result;
    }

    private void ensurePrerequisiteShopOrder(String id, String paypalOrderId, Money total, String status, String customer) {
        if (shopOrderService.findOrderById(id).isEmpty()) {
            shopOrderService.createOrder(id, paypalOrderId, total, customer);
        }
    }

    private String generateNextCaseId() {
        return caseRepository.nextCaseId();
    }
}
