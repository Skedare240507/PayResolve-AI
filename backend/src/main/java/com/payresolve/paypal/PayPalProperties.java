package com.payresolve.paypal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for PayPal sandbox integration.
 */
@ConfigurationProperties(prefix = "payresolve.paypal")
public record PayPalProperties(
    String baseUrl,
    String clientId,
    String clientSecret,
    String webhookId,
    long connectTimeoutMs,
    long readTimeoutMs,
    boolean useSyntheticDisputes
) {
    public PayPalProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api-m.sandbox.paypal.com";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 10000;
        }
    }
}
