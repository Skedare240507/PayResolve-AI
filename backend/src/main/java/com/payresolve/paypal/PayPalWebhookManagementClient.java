package com.payresolve.paypal;

import com.payresolve.paypal.model.PayPalUserInfoResponse;
import com.payresolve.paypal.model.RegisteredWebhook;
import com.payresolve.paypal.model.WebhookListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Read-only PayPal client for diagnostics: inspects registered webhooks
 * and optional identity metadata without mutating remote state.
 */
@Service
public class PayPalWebhookManagementClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookManagementClient.class);

    private final RestClient restClient;

    public PayPalWebhookManagementClient(RestClient payPalRestClient) {
        this.restClient = payPalRestClient;
    }

    /**
     * Lists webhooks registered under the configured PayPal REST app.
     *
     * @return list of registered webhook configurations
     * @throws PayPalApiException if PayPal returns an HTTP error or connection failure
     */
    public List<RegisteredWebhook> listWebhooks() {
        try {
            WebhookListResponse response = restClient.get()
                .uri("/v1/notifications/webhooks")
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new PayPalApiException(
                        res.getStatusCode().value(),
                        "Failed to list registered PayPal webhooks",
                        errorBody
                    );
                })
                .body(WebhookListResponse.class);

            return response != null ? response.webhooks() : List.of();
        } catch (PayPalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PayPalApiException(500, "Failed to connect to PayPal Webhooks API: " + e.getMessage(), null, e);
        }
    }

    /**
     * Probes PayPal Identity UserInfo to see if account email or payer ID is exposed for the app token.
     * Standard app-level client_credentials tokens commonly return 401/403 here; in that case returns Optional.empty()
     * rather than throwing so diagnostics can report non-fatal unavailability.
     */
    public Optional<PayPalUserInfoResponse> fetchUserInfoIfExposed() {
        try {
            PayPalUserInfoResponse response = restClient.get()
                .uri("/v1/identity/oauth2/userinfo?schema=paypalv1.1")
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new PayPalApiException(
                        res.getStatusCode().value(),
                        "Identity userinfo not exposed for current token",
                        null
                    );
                })
                .body(PayPalUserInfoResponse.class);

            return Optional.ofNullable(response);
        } catch (PayPalApiException e) {
            log.debug("PayPal identity userinfo not exposed (HTTP {}): {}", e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("PayPal identity userinfo lookup skipped: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

