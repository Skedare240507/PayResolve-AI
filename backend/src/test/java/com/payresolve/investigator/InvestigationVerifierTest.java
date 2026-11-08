package com.payresolve.investigator;

import com.payresolve.cases.CaseRecord;
import com.payresolve.cases.CaseSeverity;
import com.payresolve.cases.CaseStatus;
import com.payresolve.cases.CaseType;
import com.payresolve.evidence.CaseEvidenceRecord;
import com.payresolve.evidence.EvidenceKind;
import com.payresolve.evidence.EvidenceSource;
import com.payresolve.investigator.model.InvestigationReport;
import com.payresolve.investigator.model.InvestigationReport.ConfirmedFact;
import com.payresolve.investigator.model.InvestigationReport.ConflictFact;
import com.payresolve.investigator.model.InvestigationReport.MissingItem;
import com.payresolve.investigator.model.InvestigationReport.RecommendedAction;
import com.payresolve.shop.ShopOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationVerifierTest {

    private InvestigationVerifier verifier;
    private CaseRecord caseRecord;
    private ShopOrder shopOrder;
    private List<CaseEvidenceRecord> evidenceItems;

    @BeforeEach
    void setUp() {
        verifier = new InvestigationVerifier();

        caseRecord = new CaseRecord(
            "PR-0142",
            CaseType.PAID_NOT_MARKED,
            CaseStatus.OPEN,
            CaseSeverity.HIGH,
            "PP-10482",
            "ORD-10482",
            8450L,
            "USD",
            Instant.now(),
            Instant.now(),
            null,
            "Payment captured but shop order remains unconfirmed."
        );

        shopOrder = new ShopOrder(
            "ORD-10482",
            "PP-10482",
            "AWAITING_CONFIRMATION",
            8450L,
            "USD",
            "Sarah Jenkins",
            Instant.now().minusSeconds(300),
            Instant.now().minusSeconds(300)
        );

        evidenceItems = List.of(
            new CaseEvidenceRecord("EVI-order-1", "PR-0142", EvidenceKind.SHOP_ORDER, EvidenceSource.SHOP, "ORD-10482", "{}", Instant.now()),
            new CaseEvidenceRecord("EVI-capture-1", "PR-0142", EvidenceKind.PAYPAL_CAPTURE, EvidenceSource.PAYPAL, "CAP-10482", "{}", Instant.now())
        );
    }

    @Test
    @DisplayName("Verifier accepts valid statements with known IDs and correct amounts")
    void shouldAcceptValidStatement() {
        InvestigationReport rawReport = new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Payment capture of $84.50 completed on PayPal.", List.of("PP-10482", "ORD-10482"))
            ),
            List.of(
                new ConflictFact("PayPal status is COMPLETED but shop order status is unconfirmed.", List.of("ORD-10482"))
            ),
            List.of(),
            new RecommendedAction("SYNC_ORDER_STATUS", "Sync order status", List.of("ORD-10482")),
            false,
            "Valid explanation"
        );

        InvestigationReport verified = verifier.verify(
            rawReport, caseRecord, evidenceItems, Optional.of(shopOrder), List.of(), List.of(), List.of(), List.of()
        );

        assertThat(verified.status()).isEqualTo("CAUSE_FOUND");
        assertThat(verified.confirmed()).hasSize(1);
        assertThat(verified.confirmed().get(0).statement()).contains("$84.50");
        assertThat(verified.confirmed().get(0).evidenceIds()).containsExactlyInAnyOrder("PP-10482", "ORD-10482");
    }

    @Test
    @DisplayName("Verifier rejects statements citing unknown / fabricated evidence IDs")
    void shouldRejectStatementsWithUnknownIds() {
        InvestigationReport rawReport = new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Citing a completely fake order number", List.of("FAKE-ORDER-99999", "UNKNOWN-PP-123")),
                new ConfirmedFact("Citing real order ORD-10482", List.of("ORD-10482"))
            ),
            List.of(),
            List.of(),
            new RecommendedAction("NONE", "None", List.of("FAKE-ID")),
            false,
            "Test fake citation"
        );

        InvestigationReport verified = verifier.verify(
            rawReport, caseRecord, evidenceItems, Optional.of(shopOrder), List.of(), List.of(), List.of(), List.of()
        );

        // The fake statement must be dropped, valid statement retained
        assertThat(verified.confirmed()).hasSize(1);
        assertThat(verified.confirmed().get(0).statement()).contains("ORD-10482");
        assertThat(verified.recommendedAction().evidenceIds()).isEmpty();
    }

    @Test
    @DisplayName("Verifier rejects statements citing wrong monetary amounts")
    void shouldRejectStatementsWithWrongAmounts() {
        InvestigationReport rawReport = new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Customer paid $999.00 on PayPal.", List.of("PP-10482")),
                new ConfirmedFact("Customer paid $84.50 on PayPal.", List.of("PP-10482"))
            ),
            List.of(),
            List.of(),
            new RecommendedAction("SYNC_ORDER_STATUS", "Reconcile", List.of("PP-10482")),
            false,
            "Test wrong amount"
        );

        InvestigationReport verified = verifier.verify(
            rawReport, caseRecord, evidenceItems, Optional.of(shopOrder), List.of(), List.of(), List.of(), List.of()
        );

        // Statement with $999.00 must be dropped; $84.50 retained
        assertThat(verified.confirmed()).hasSize(1);
        assertThat(verified.confirmed().get(0).statement()).contains("$84.50");
    }

    @Test
    @DisplayName("Verifier transitions status to CAUSE_NOT_DETERMINED when all confirmed statements fail verification")
    void shouldTransitionToCauseNotDeterminedWhenAllFail() {
        InvestigationReport rawReport = new InvestigationReport(
            "CAUSE_FOUND",
            List.of(
                new ConfirmedFact("Fabricated claim about $999.00", List.of("FAKE-ID-1")),
                new ConfirmedFact("Another hallucinated fact", List.of("UNKNOWN-ID-2"))
            ),
            List.of(),
            List.of(new MissingItem("Proof", "Missing")),
            new RecommendedAction("REQUEST_REVIEW", "Review needed", List.of()),
            false,
            "Inconclusive"
        );

        InvestigationReport verified = verifier.verify(
            rawReport, caseRecord, evidenceItems, Optional.of(shopOrder), List.of(), List.of(), List.of(), List.of()
        );

        assertThat(verified.status()).isEqualTo("CAUSE_NOT_DETERMINED");
        assertThat(verified.confirmed()).isEmpty();
    }
}

