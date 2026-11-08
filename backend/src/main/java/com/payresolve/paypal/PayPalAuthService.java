package com.payresolve.paypal;

import com.payresolve.paypal.model.PayPalTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OAuth token client for PayPal Sandbox with proactive in-memory caching,
 * expiry buffer, and strict credential redaction.
 */
@Service
public class PayPalAuthService {

    private static final Logger log = LoggerFactory.getLogger(PayPalAuthService.class);
    private static final int EXPIRY_BUFFER_SECONDS = 120;

    private final PayPalProperties properties;
    private final Clock clock;
    private final RestClient authRestClient;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    public PayPalAuthService(PayPalProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
            .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.authRestClient = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(new BufferingClientHttpRequestFactory(factory))
            .build();
    }

    /**
     * Returns a valid Bearer token, proactively refreshing if within 120s of expiration.
     */
    public String getValidAccessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS))) {
            return cachedToken;
        }

        lock.lock();
        try {
            // Double-check under lock
            now = clock.instant();
            if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS))) {
                return cachedToken;
            }
            refreshToken();
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Explicitly invalidates the cached token (e.g. after receiving a 401 Unauthorized from PayPal).
     */
    public void invalidateToken() {
        lock.lock();
        try {
            log.info("Invalidating cached PayPal access token");
            this.cachedToken = null;
            this.tokenExpiresAt = Instant.MIN;
        } finally {
            lock.unlock();
        }
    }

    private void refreshToken() {
        if (properties.clientId() == null || properties.clientId().isBlank()
            || properties.clientSecret() == null || properties.clientSecret().isBlank()) {
            throw new PayPalApiException(
                401,
                "PayPal client-id or client-secret is missing in configuration",
                null
            );
        }

        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(
            (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8)
        );

        log.debug("Requesting fresh PayPal OAuth2 token from {}", properties.baseUrl());

        try {
            PayPalTokenResponse response = authRestClient.post()
                .uri("/v1/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new PayPalApiException(res.getStatusCode().value(), "OAuth token exchange failed", errorBody);
                })
                .body(PayPalTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new PayPalApiException(500, "Empty OAuth token response from PayPal", null);
            }

            long expiresInSeconds = (response.expiresIn() != null && response.expiresIn() > 0)
                ? response.expiresIn()
                : 3600L;

            this.cachedToken = response.accessToken();
            this.tokenExpiresAt = clock.instant().plusSeconds(expiresInSeconds);

            log.info("Successfully obtained PayPal access token (expires in {}s)", expiresInSeconds);
        } catch (PayPalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to authenticate with PayPal: {}", e.getMessage());
            throw new PayPalApiException(500, "Failed to connect to PayPal OAuth token service: " + e.getMessage(), null, e);
        }
    }

    /**
     * Visible for testing token caching inspection.
     */
    Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }
}
