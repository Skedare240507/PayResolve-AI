package com.payresolve.paypal;

import com.payresolve.paypal.model.CaptureDetails;
import com.payresolve.paypal.model.RefundDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Client for PayPal Payments v2 API (read-only queries for captures and refunds).
 * Note: Under money-safety rules, no methods move money or issue refunds from this application.
 */
@Service
public class PayPalPaymentsClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentsClient.class);
    private final RestClient restClient;

    public PayPalPaymentsClient(RestClient payPalRestClient) {
        this.restClient = payPalRestClient;
    }

    /**
     * Retrieves details for a specific capture transaction.
     */
    public CaptureDetails getCapture(String captureId) {
        Objects.requireNonNull(captureId, "captureId must not be null");
        log.debug("Retrieving capture details for captureId={}", captureId);

        return restClient.get()
            .uri("/v2/payments/captures/{id}", captureId)
            .retrieve()
            .onStatus(status -> status.isError(), (req, res) -> {
                String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new PayPalApiException(res.getStatusCode().value(), "Failed to retrieve capture: " + captureId, errorBody);
            })
            .body(CaptureDetails.class);
    }

    /**
     * Retrieves details for a specific refund transaction.
     */
    public RefundDetails getRefund(String refundId) {
        Objects.requireNonNull(refundId, "refundId must not be null");
        log.debug("Retrieving refund details for refundId={}", refundId);

        return restClient.get()
            .uri("/v2/payments/refunds/{id}", refundId)
            .retrieve()
            .onStatus(status -> status.isError(), (req, res) -> {
                String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new PayPalApiException(res.getStatusCode().value(), "Failed to retrieve refund: " + refundId, errorBody);
            })
            .body(RefundDetails.class);
    }
}
