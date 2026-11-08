package com.payresolve.paypal;

import com.payresolve.paypal.model.DisputeDetail;
import com.payresolve.paypal.model.DisputeListResponse;

/**
 * Service interface for querying PayPal customer disputes.
 * Implementations provide either live API communication with PayPal Sandbox or
 * clearly labelled synthetic dispute fixtures.
 */
public interface PayPalDisputesClient {

    /**
     * Lists recent customer disputes.
     */
    DisputeListResponse listDisputes();

    /**
     * Retrieves full details of a specific dispute.
     */
    DisputeDetail getDispute(String disputeId);
}
