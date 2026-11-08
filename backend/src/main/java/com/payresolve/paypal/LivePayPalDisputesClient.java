package com.payresolve.paypal;

import com.payresolve.paypal.model.DisputeDetail;
import com.payresolve.paypal.model.DisputeListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * Live PayPal Disputes API client with automatic, clearly labelled fallback
 * to synthetic dispute fixtures when sandbox scopes are absent or synthetic mode is enabled.
 */
@Service
@Primary
public class LivePayPalDisputesClient implements PayPalDisputesClient {

    private static final Logger log = LoggerFactory.getLogger(LivePayPalDisputesClient.class);
    public static final String SOURCE_PAYPAL = "PAYPAL";

    private final RestClient restClient;
    private final PayPalProperties properties;
    private final SyntheticDisputesProvider syntheticProvider;

    public LivePayPalDisputesClient(
        RestClient payPalRestClient,
        PayPalProperties properties,
        SyntheticDisputesProvider syntheticProvider
    ) {
        this.restClient = payPalRestClient;
        this.properties = properties;
        this.syntheticProvider = syntheticProvider;
    }

    @Override
    public DisputeListResponse listDisputes() {
        if (properties.useSyntheticDisputes()) {
            log.info("Synthetic disputes mode configured; using synthetic fixtures");
            return syntheticProvider.listDisputes();
        }

        try {
            log.debug("Calling PayPal GET /v1/customer/disputes");
            DisputeListResponse response = restClient.get()
                .uri("/v1/customer/disputes")
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new PayPalApiException(res.getStatusCode().value(), "Failed to query PayPal disputes", errorBody);
                })
                .body(DisputeListResponse.class);

            if (response == null) {
                return syntheticProvider.listDisputes();
            }
            return response.withSynthetic(false, SOURCE_PAYPAL);
        } catch (PayPalApiException e) {
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                log.warn("PayPal Disputes API returned HTTP {} (sandbox dispute scopes typically require special permissions). "
                    + "Falling back to labelled synthetic fixtures.", e.getStatusCode());
                return syntheticProvider.listDisputes();
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to reach PayPal Disputes API: {}. Falling back to labelled synthetic fixtures.", e.getMessage(), e);
            return syntheticProvider.listDisputes();
        }
    }

    @Override
    public DisputeDetail getDispute(String disputeId) {
        if (properties.useSyntheticDisputes()) {
            return syntheticProvider.getDispute(disputeId);
        }

        try {
            log.debug("Calling PayPal GET /v1/customer/disputes/{}", disputeId);
            DisputeDetail detail = restClient.get()
                .uri("/v1/customer/disputes/{id}", disputeId)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new PayPalApiException(res.getStatusCode().value(), "Failed to query dispute: " + disputeId, errorBody);
                })
                .body(DisputeDetail.class);

            if (detail == null) {
                return syntheticProvider.getDispute(disputeId);
            }
            return detail.withSynthetic(false, SOURCE_PAYPAL);
        } catch (PayPalApiException e) {
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                log.warn("PayPal Dispute GET returned HTTP {}. Falling back to labelled synthetic fixture for disputeId={}",
                    e.getStatusCode(), disputeId);
                return syntheticProvider.getDispute(disputeId);
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to reach PayPal Dispute details: {}. Falling back to labelled synthetic fixture.", e.getMessage());
            return syntheticProvider.getDispute(disputeId);
        }
    }
}
