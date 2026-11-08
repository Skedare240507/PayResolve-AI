package com.payresolve.paypal;

import com.payresolve.paypal.model.Amount;
import com.payresolve.paypal.model.DisputeDetail;
import com.payresolve.paypal.model.DisputeEvidence;
import com.payresolve.paypal.model.DisputeListResponse;
import com.payresolve.paypal.model.DisputeMessage;
import com.payresolve.paypal.model.DisputeSummary;
import com.payresolve.paypal.model.DisputedTransaction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fixture provider for synthetic PayPal dispute data.
 * All fixtures are explicitly labelled with synthetic=true and source="SYNTHETIC"
 * in adherence with hackathon money-safety rules.
 */
@Component
public class SyntheticDisputesProvider implements PayPalDisputesClient {

    public static final String SOURCE_SYNTHETIC = "SYNTHETIC";

    private final List<DisputeDetail> fixtures;

    public SyntheticDisputesProvider() {
        this.fixtures = createFixtures();
    }

    @Override
    public DisputeListResponse listDisputes() {
        List<DisputeSummary> summaries = fixtures.stream()
            .map(this::toSummary)
            .toList();
        return new DisputeListResponse(summaries, List.of(), true, SOURCE_SYNTHETIC);
    }

    @Override
    public DisputeDetail getDispute(String disputeId) {
        return fixtures.stream()
            .filter(d -> d.disputeId().equalsIgnoreCase(disputeId))
            .findFirst()
            .orElseGet(() -> buildDefaultDetail(disputeId));
    }

    private DisputeSummary toSummary(DisputeDetail detail) {
        return new DisputeSummary(
            detail.disputeId(),
            detail.createTime(),
            detail.updateTime(),
            detail.disputeAmount(),
            detail.reason(),
            detail.status(),
            detail.disputeState(),
            detail.disputeLifeCycleStage(),
            detail.disputeChannel(),
            true,
            SOURCE_SYNTHETIC
        );
    }

    private List<DisputeDetail> createFixtures() {
        DisputeDetail dispute1 = new DisputeDetail(
            "PP-D-38912",
            "2026-11-06T14:32:00Z",
            "2026-11-07T09:15:00Z",
            new Amount("USD", "84.50"),
            "MERCHANDISE_OR_SERVICE_NOT_RECEIVED",
            "UNDER_REVIEW",
            "REQUIRED_ACTION",
            "CHARGEBACK",
            "INTERNAL",
            List.of(new DisputedTransaction("BUYER-TX-9912", "SELLER-TX-1184", new Amount("USD", "84.50"))),
            List.of(new DisputeMessage("BUYER", "Order marked delivered but package was never received.", "2026-11-06T14:35:00Z")),
            List.of(new DisputeEvidence("PROOF_OF_FULFILLMENT", "Tracking reference fedex-789456-tr")),
            true,
            SOURCE_SYNTHETIC
        );

        DisputeDetail dispute2 = new DisputeDetail(
            "PP-D-49021",
            "2026-11-05T18:00:00Z",
            "2026-11-06T10:00:00Z",
            new Amount("USD", "120.00"),
            "UNAUTHORISED",
            "WAITING_FOR_SELLER_RESPONSE",
            "REQUIRED_ACTION",
            "INQUIRY",
            "INTERNAL",
            List.of(new DisputedTransaction("BUYER-TX-5541", "SELLER-TX-2290", new Amount("USD", "120.00"))),
            List.of(new DisputeMessage("BUYER", "Unrecognized debit charge on card.", "2026-11-05T18:05:00Z")),
            List.of(),
            true,
            SOURCE_SYNTHETIC
        );

        return List.of(dispute1, dispute2);
    }

    private DisputeDetail buildDefaultDetail(String disputeId) {
        return new DisputeDetail(
            disputeId,
            "2026-11-07T00:00:00Z",
            "2026-11-07T00:00:00Z",
            new Amount("USD", "50.00"),
            "MERCHANDISE_OR_SERVICE_NOT_RECEIVED",
            "UNDER_REVIEW",
            "REQUIRED_ACTION",
            "INQUIRY",
            "INTERNAL",
            List.of(),
            List.of(),
            List.of(),
            true,
            SOURCE_SYNTHETIC
        );
    }
}
