package com.payresolve.investigator;

import com.payresolve.cases.CaseRecord;
import com.payresolve.evidence.CaseEvidenceRecord;
import com.payresolve.ingest.WebhookEvent;
import com.payresolve.investigator.model.InvestigationReport;
import com.payresolve.investigator.model.InvestigationReport.ConfirmedFact;
import com.payresolve.investigator.model.InvestigationReport.ConflictFact;
import com.payresolve.investigator.model.InvestigationReport.RecommendedAction;
import com.payresolve.shop.PaymentRecord;
import com.payresolve.shop.RefundRecord;
import com.payresolve.shop.ShipmentRecord;
import com.payresolve.shop.ShopOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic verifier ensuring every claim, cited evidence ID, and monetary figure
 * in an AI InvestigationReport corresponds to verified facts in the database and PayPal state.
 * Statements citing unknown IDs or incorrect amounts are dropped or flagged.
 */
@Component
public class InvestigationVerifier {

    private static final Logger log = LoggerFactory.getLogger(InvestigationVerifier.class);
    private static final Pattern DOLLAR_AMOUNT_PATTERN = Pattern.compile("\\$(\\d+(?:\\.\\d{1,2})?)");

    /**
     * Verifies the report against deterministic ground truth.
     *
     * @param rawReport report output from the AI model
     * @param caseRecord the case being investigated
     * @param evidenceItems all normalized evidence items for the case
     * @param shopOrder merchant shop order if known
     * @param payments payment records if known
     * @param refunds refund records if known
     * @param shipments shipment records if known
     * @param webhooks webhook events matching the case or order
     * @return verified report with false citations pruned/flagged and status reconciled
     */
    public InvestigationReport verify(
        InvestigationReport rawReport,
        CaseRecord caseRecord,
        List<CaseEvidenceRecord> evidenceItems,
        Optional<ShopOrder> shopOrder,
        List<PaymentRecord> payments,
        List<RefundRecord> refunds,
        List<ShipmentRecord> shipments,
        List<WebhookEvent> webhooks
    ) {
        if (rawReport == null) {
            return buildFallbackReport(caseRecord, "No report provided by investigator.");
        }

        Set<String> validIds = buildValidIdsSet(caseRecord, evidenceItems, shopOrder, payments, refunds, shipments, webhooks);
        Set<Long> validAmountsMinor = buildValidAmountsSet(caseRecord, shopOrder, payments, refunds);

        log.debug("Verification ground truth for case {}: {} valid IDs, {} valid amounts",
            caseRecord.id(), validIds.size(), validAmountsMinor.size());

        List<ConfirmedFact> verifiedConfirmed = new ArrayList<>();
        int droppedConfirmedCount = 0;

        for (ConfirmedFact fact : rawReport.confirmed()) {
            VerificationResult vr = verifyStatement(fact.statement(), fact.evidenceIds(), validIds, validAmountsMinor);
            if (vr.isValid()) {
                verifiedConfirmed.add(new ConfirmedFact(vr.statement(), vr.sanitizedIds()));
            } else {
                log.warn("Dropping unverified confirmed statement: '{}' (reason: {})", fact.statement(), vr.rejectionReason());
                droppedConfirmedCount++;
            }
        }

        List<ConflictFact> verifiedConflicts = new ArrayList<>();
        for (ConflictFact conflict : rawReport.conflicts()) {
            VerificationResult vr = verifyStatement(conflict.statement(), conflict.evidenceIds(), validIds, validAmountsMinor);
            if (vr.isValid()) {
                verifiedConflicts.add(new ConflictFact(vr.statement(), vr.sanitizedIds()));
            } else {
                log.warn("Dropping unverified conflict statement: '{}' (reason: {})", conflict.statement(), vr.rejectionReason());
            }
        }

        RecommendedAction verifiedAction = rawReport.recommendedAction();
        if (verifiedAction != null) {
            List<String> validActionIds = filterValidIds(verifiedAction.evidenceIds(), validIds);
            verifiedAction = new RecommendedAction(
                verifiedAction.type() != null ? verifiedAction.type() : "NONE",
                verifiedAction.rationale(),
                validActionIds
            );
        }

        String finalStatus = rawReport.status();
        if (verifiedConfirmed.isEmpty() && !"CAUSE_NOT_DETERMINED".equalsIgnoreCase(finalStatus)) {
            log.info("No confirmed facts survived verification for case {}. Setting status to CAUSE_NOT_DETERMINED.", caseRecord.id());
            finalStatus = "CAUSE_NOT_DETERMINED";
        }

        boolean isDegraded = Boolean.TRUE.equals(rawReport.degraded()) || droppedConfirmedCount > 0 && verifiedConfirmed.isEmpty();

        return new InvestigationReport(
            finalStatus,
            verifiedConfirmed,
            verifiedConflicts,
            rawReport.missing(),
            verifiedAction,
            isDegraded,
            rawReport.explanation()
        );
    }

    private VerificationResult verifyStatement(
        String statement,
        List<String> citedIds,
        Set<String> validIds,
        Set<Long> validAmountsMinor
    ) {
        if (statement == null || statement.isBlank()) {
            return VerificationResult.invalid("Empty statement");
        }

        // 1. Verify cited IDs
        List<String> validCited = filterValidIds(citedIds, validIds);
        if (!citedIds.isEmpty() && validCited.isEmpty()) {
            return VerificationResult.invalid("All cited IDs are unknown to the system: " + citedIds);
        }

        // 2. Verify mentioned amounts
        Matcher matcher = DOLLAR_AMOUNT_PATTERN.matcher(statement);
        while (matcher.find()) {
            try {
                double dollarVal = Double.parseDouble(matcher.group(1));
                long minorVal = Math.round(dollarVal * 100);
                if (!validAmountsMinor.contains(minorVal)) {
                    return VerificationResult.invalid(
                        String.format("Cited amount $%s (minor %d) does not match any known record amount",
                            matcher.group(1), minorVal));
                }
            } catch (Exception ignored) {
            }
        }

        return VerificationResult.valid(statement, validCited);
    }

    private List<String> filterValidIds(List<String> candidateIds, Set<String> validIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (String id : candidateIds) {
            if (id != null && isKnownId(id.trim(), validIds)) {
                filtered.add(id.trim());
            }
        }
        return filtered;
    }

    private boolean isKnownId(String candidate, Set<String> validIds) {
        if (validIds.contains(candidate.toUpperCase(Locale.ROOT))) {
            return true;
        }
        // Substring / partial match for composite IDs like CAP-10482 or PP-10482
        for (String valid : validIds) {
            if (valid.equalsIgnoreCase(candidate) || candidate.contains(valid) || valid.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> buildValidIdsSet(
        CaseRecord caseRecord,
        List<CaseEvidenceRecord> evidenceItems,
        Optional<ShopOrder> shopOrder,
        List<PaymentRecord> payments,
        List<RefundRecord> refunds,
        List<ShipmentRecord> shipments,
        List<WebhookEvent> webhooks
    ) {
        Set<String> valid = new HashSet<>();
        if (caseRecord.id() != null) valid.add(caseRecord.id().toUpperCase(Locale.ROOT));
        if (caseRecord.paypalOrderId() != null) valid.add(caseRecord.paypalOrderId().toUpperCase(Locale.ROOT));
        if (caseRecord.shopOrderId() != null) valid.add(caseRecord.shopOrderId().toUpperCase(Locale.ROOT));

        for (CaseEvidenceRecord e : evidenceItems) {
            if (e.id() != null) valid.add(e.id().toUpperCase(Locale.ROOT));
            if (e.externalId() != null) valid.add(e.externalId().toUpperCase(Locale.ROOT));
        }

        shopOrder.ifPresent(order -> {
            if (order.id() != null) valid.add(order.id().toUpperCase(Locale.ROOT));
            if (order.paypalOrderId() != null) valid.add(order.paypalOrderId().toUpperCase(Locale.ROOT));
        });

        for (PaymentRecord p : payments) {
            if (p.id() != null) valid.add(p.id().toUpperCase(Locale.ROOT));
            if (p.paypalCaptureId() != null) valid.add(p.paypalCaptureId().toUpperCase(Locale.ROOT));
        }

        for (RefundRecord r : refunds) {
            if (r.id() != null) valid.add(r.id().toUpperCase(Locale.ROOT));
            if (r.paypalRefundId() != null) valid.add(r.paypalRefundId().toUpperCase(Locale.ROOT));
        }

        for (ShipmentRecord s : shipments) {
            if (s.id() != null) valid.add(s.id().toUpperCase(Locale.ROOT));
            if (s.trackingRef() != null) valid.add(s.trackingRef().toUpperCase(Locale.ROOT));
        }

        for (WebhookEvent w : webhooks) {
            if (w.paypalEventId() != null) valid.add(w.paypalEventId().toUpperCase(Locale.ROOT));
            valid.add(("WH-EVT-" + w.id()).toUpperCase(Locale.ROOT));
        }

        return valid;
    }

    private Set<Long> buildValidAmountsSet(
        CaseRecord caseRecord,
        Optional<ShopOrder> shopOrder,
        List<PaymentRecord> payments,
        List<RefundRecord> refunds
    ) {
        Set<Long> amounts = new HashSet<>();
        if (caseRecord.amountMinor() != null) {
            amounts.add(caseRecord.amountMinor());
        }
        shopOrder.ifPresent(order -> amounts.add(order.totalMinor()));
        for (PaymentRecord p : payments) {
            amounts.add(p.amountMinor());
        }
        for (RefundRecord r : refunds) {
            amounts.add(r.amountMinor());
        }

        // Add calculated differences (e.g. for amount mismatch $5.00 delta between $84.50 and $79.50)
        if (caseRecord.amountMinor() != null && shopOrder.isPresent()) {
            long delta = Math.abs(caseRecord.amountMinor() - shopOrder.get().totalMinor());
            amounts.add(delta);
        }

        return amounts;
    }

    private InvestigationReport buildFallbackReport(CaseRecord caseRecord, String reason) {
        return new InvestigationReport(
            "CAUSE_NOT_DETERMINED",
            List.of(),
            List.of(),
            List.of(new InvestigationReport.MissingItem("Conclusive evidence", reason)),
            new InvestigationReport.RecommendedAction("REQUEST_REVIEW", "Deterministic verification could not validate cause", List.of()),
            true,
            reason
        );
    }

    private static class VerificationResult {
        private final boolean valid;
        private final String statement;
        private final List<String> sanitizedIds;
        private final String rejectionReason;

        private VerificationResult(boolean valid, String statement, List<String> sanitizedIds, String rejectionReason) {
            this.valid = valid;
            this.statement = statement;
            this.sanitizedIds = sanitizedIds != null ? sanitizedIds : List.of();
            this.rejectionReason = rejectionReason;
        }

        public static VerificationResult valid(String statement, List<String> sanitizedIds) {
            return new VerificationResult(true, statement, sanitizedIds, null);
        }

        public static VerificationResult invalid(String rejectionReason) {
            return new VerificationResult(false, null, List.of(), rejectionReason);
        }

        public boolean isValid() { return valid; }
        public String statement() { return statement; }
        public List<String> sanitizedIds() { return sanitizedIds; }
        public String rejectionReason() { return rejectionReason; }
    }
}

