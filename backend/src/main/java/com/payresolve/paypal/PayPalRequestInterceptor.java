package com.payresolve.paypal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * ClientHttpRequestInterceptor for PayPal REST API calls:
 * - Automatically injects valid Bearer token from PayPalAuthService.
 * - Redacts secrets and authorization tokens from all logs.
 * - Handles 401 Unauthorized by proactively invalidating token and retrying once.
 * - Handles transient network errors and 5xx responses with 1 retry with jitter.
 */
public class PayPalRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PayPalRequestInterceptor.class);
    private final PayPalAuthService authService;
    private final Random random = new Random();

    public PayPalRequestInterceptor(PayPalAuthService authService) {
        this.authService = authService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));

        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            String token = authService.getValidAccessToken();
            request.getHeaders().setBearerAuth(token);
        }

        if (log.isDebugEnabled()) {
            // Note: Never log authorization headers or secrets
            log.debug("Calling PayPal API: {} {}", request.getMethod(), request.getURI().getPath());
        }

        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException ex) {
            log.warn("Network error during PayPal call ({} {}), retrying once with jitter: {}",
                request.getMethod(), request.getURI().getPath(), ex.getMessage());
            applyJitter();
            return execution.execute(request, body);
        }

        // Handle 401 Unauthorized: token expired or revoked, refresh and retry once
        if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            log.warn("Received 401 Unauthorized from PayPal ({} {}), refreshing token and retrying",
                request.getMethod(), request.getURI().getPath());
            response.close();
            authService.invalidateToken();
            String newToken = authService.getValidAccessToken();
            request.getHeaders().setBearerAuth(newToken);
            return execution.execute(request, body);
        }

        // Handle transient 5xx or 429
        if (response.getStatusCode().is5xxServerError() || response.getStatusCode().value() == 429) {
            log.warn("Received status {} from PayPal ({} {}), retrying once with jitter",
                response.getStatusCode().value(), request.getMethod(), request.getURI().getPath());
            response.close();
            applyJitter();
            return execution.execute(request, body);
        }

        return response;
    }

    private void applyJitter() {
        try {
            long jitterMs = 50 + random.nextInt(100);
            Thread.sleep(jitterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
