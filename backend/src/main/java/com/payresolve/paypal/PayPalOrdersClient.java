package com.payresolve.paypal;

import com.payresolve.common.Money;
import com.payresolve.paypal.model.CreateOrderRequest;
import com.payresolve.paypal.model.PayPalOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Client for PayPal Orders v2 API.
 * Handles order creation (with custom_id = shop order ID), status checks, and server-side capture.
 */
@Service
public class PayPalOrdersClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalOrdersClient.class);
    private final RestClient restClient;

    public PayPalOrdersClient(RestClient payPalRestClient) {
        this.restClient = payPalRestClient;
    }

    /**
     * Creates an order with PayPal Orders v2, embedding the shopOrderId in custom_id.
     */
    public PayPalOrder createOrder(String shopOrderId, Money amount, String returnUrl, String cancelUrl) {
        Objects.requireNonNull(shopOrderId, "shopOrderId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        CreateOrderRequest request = CreateOrderRequest.of(shopOrderId, amount, returnUrl, cancelUrl);
        String requestId = UUID.randomUUID().toString();

        log.info("Creating PayPal order for shopOrderId={} amount={} (requestId={})",
            shopOrderId, amount, requestId);

        return restClient.post()
            .uri("/v2/checkout/orders")
            .header("PayPal-Request-Id", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(status -> status.isError(), (req, res) -> {
                String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new PayPalApiException(res.getStatusCode().value(), "Failed to create PayPal order", errorBody);
            })
            .body(PayPalOrder.class);
    }

    /**
     * Retrieves details of an existing PayPal Order.
     */
    public PayPalOrder getOrder(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        log.debug("Retrieving PayPal order details for orderId={}", orderId);

        return restClient.get()
            .uri("/v2/checkout/orders/{id}", orderId)
            .retrieve()
            .onStatus(status -> status.isError(), (req, res) -> {
                String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new PayPalApiException(res.getStatusCode().value(), "Failed to retrieve PayPal order: " + orderId, errorBody);
            })
            .body(PayPalOrder.class);
    }

    /**
     * Captures payment for an approved PayPal Order server-side.
     */
    public PayPalOrder captureOrder(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        String requestId = UUID.randomUUID().toString();

        log.info("Executing server-side capture for PayPal orderId={} (requestId={})", orderId, requestId);

        return restClient.post()
            .uri("/v2/checkout/orders/{id}/capture", orderId)
            .header("PayPal-Request-Id", requestId)
            .header("Prefer", "return=representation")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .retrieve()
            .onStatus(status -> status.isError(), (req, res) -> {
                String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new PayPalApiException(res.getStatusCode().value(), "Failed to capture PayPal order: " + orderId, errorBody);
            })
            .body(PayPalOrder.class);
    }
}
