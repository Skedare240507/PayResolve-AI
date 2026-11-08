package com.payresolve.paypal;

import com.payresolve.paypal.model.VerifyWebhookSignatureRequest;
import com.payresolve.paypal.model.VerifyWebhookSignatureResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Client for PayPal's Webhook Signature Verification API (POST /v1/notifications/verify-webhook-signature).
 */
@Service
public class PayPalWebhookVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookVerificationClient.class);

    private final RestClient restClient;
    private final PayPalProperties properties;

    public PayPalWebhookVerificationClient(RestClient payPalRestClient, PayPalProperties properties) {
        this.restClient = payPalRestClient;
        this.properties = properties;
    }

    /**
     * Verifies the signature of an incoming PayPal webhook notification.
     *
     * @param headers incoming HTTP headers (case-insensitive lookup supported)
     * @param webhookEvent the parsed JSON object or map of the webhook event body
     * @return true if PayPal verifies the signature, false otherwise
     */
    public boolean verifySignature(Map<String, String> headers, Object webhookEvent) {
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(webhookEvent, "webhookEvent must not be null");

        String webhookId = properties.webhookId();
        if (webhookId == null || webhookId.isBlank()) {
            log.error("Cannot verify webhook signature: PAYPAL_WEBHOOK_ID is not configured");
            return false;
        }

        String authAlgo = getHeaderCaseInsensitive(headers, "paypal-auth-algo");
        String certUrl = getHeaderCaseInsensitive(headers, "paypal-cert-url");
        String transmissionId = getHeaderCaseInsensitive(headers, "paypal-transmission-id");
        String transmissionSig = getHeaderCaseInsensitive(headers, "paypal-transmission-sig");
        String transmissionTime = getHeaderCaseInsensitive(headers, "paypal-transmission-time");

        if (authAlgo == null || certUrl == null || transmissionId == null
            || transmissionSig == null || transmissionTime == null) {
            log.warn("Missing required PayPal verification headers in incoming webhook request: transmissionId={}", transmissionId);
            return false;
        }

        VerifyWebhookSignatureRequest request = new VerifyWebhookSignatureRequest(
            authAlgo,
            certUrl,
            transmissionId,
            transmissionSig,
            transmissionTime,
            webhookId,
            webhookEvent
        );

        try {
            log.debug("Calling PayPal verify-webhook-signature for transmissionId={}", transmissionId);

            VerifyWebhookSignatureResponse response = restClient.post()
                .uri("/v1/notifications/verify-webhook-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new PayPalApiException(res.getStatusCode().value(), "Webhook signature verification call failed", errorBody);
                })
                .body(VerifyWebhookSignatureResponse.class);

            if (response != null && response.isSuccess()) {
                log.info("PayPal webhook signature verified successfully (transmissionId={})", transmissionId);
                return true;
            } else {
                log.warn("PayPal webhook signature verification failed: status={} (transmissionId={})",
                    response != null ? response.verificationStatus() : "null", transmissionId);
                return false;
            }
        } catch (PayPalApiException e) {
            log.error("PayPal API error during webhook signature verification: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during webhook signature verification: {}", e.getMessage(), e);
            return false;
        }
    }

    private String getHeaderCaseInsensitive(Map<String, String> headers, String targetKey) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(targetKey)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
