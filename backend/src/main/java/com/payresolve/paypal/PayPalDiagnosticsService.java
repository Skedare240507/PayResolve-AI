package com.payresolve.paypal;

import com.payresolve.paypal.dto.DiagnosticCheckDto;
import com.payresolve.paypal.dto.PayPalDiagnosticsDto;
import com.payresolve.paypal.model.PayPalUserInfoResponse;
import com.payresolve.paypal.model.RegisteredWebhook;
import com.payresolve.paypal.model.WebhookEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operator diagnostics service evaluating PayPal Sandbox configuration, OAuth credentials,
 * registered webhooks (without leaking secrets), last received webhook timestamp, and sandbox account identity.
 */
@Service
public class PayPalDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(PayPalDiagnosticsService.class);
    static final String EXPECTED_SANDBOX_BASE_URL = "https://api-m.sandbox.paypal.com";
    static final List<String> REQUIRED_WEBHOOK_EVENTS = List.of(
        "PAYMENT.CAPTURE.COMPLETED",
        "PAYMENT.CAPTURE.REFUNDED",
        "CHECKOUT.ORDER.APPROVED"
    );

    private final PayPalProperties properties;
    private final PayPalAuthService authService;
    private final PayPalWebhookManagementClient webhookManagementClient;
    private final LastWebhookTimeProvider lastWebhookTimeProvider;
    private final String publicUrl;
    private final Clock clock;

    public PayPalDiagnosticsService(
        PayPalProperties properties,
        PayPalAuthService authService,
        PayPalWebhookManagementClient webhookManagementClient,
        LastWebhookTimeProvider lastWebhookTimeProvider,
        @Value("${payresolve.public-url:http://localhost:8080}") String publicUrl,
        Clock clock
    ) {
        this.properties = properties;
        this.authService = authService;
        this.webhookManagementClient = webhookManagementClient;
        this.lastWebhookTimeProvider = lastWebhookTimeProvider;
        this.publicUrl = publicUrl;
        this.clock = clock;
    }

    /**
     * Runs live PayPal Sandbox configuration and connectivity checks without exposing any secret values.
     */
    public PayPalDiagnosticsDto runDiagnostics() {
        List<DiagnosticCheckDto> checks = new ArrayList<>();
        String normalizedBaseUrl = stripTrailingSlash(properties.baseUrl());
        String normalizedPublicUrl = stripTrailingSlash(publicUrl);
        String expectedWebhookUrl = normalizedPublicUrl + "/api/webhooks/paypal";

        // 1. Check PAYPAL_BASE_URL points to PayPal Sandbox
        boolean sandboxUrlOk = EXPECTED_SANDBOX_BASE_URL.equalsIgnoreCase(normalizedBaseUrl);
        checks.add(new DiagnosticCheckDto(
            "sandbox_base_url",
            "PayPal Sandbox Base URL",
            sandboxUrlOk ? "PASS" : "FAIL",
            sandboxUrlOk,
            sandboxUrlOk
                ? "PAYPAL_BASE_URL targets PayPal Sandbox (" + normalizedBaseUrl + ")."
                : "PAYPAL_BASE_URL is \"" + normalizedBaseUrl + "\" instead of PayPal Sandbox.",
            sandboxUrlOk
                ? null
                : "Set PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com so payments run only against PayPal Sandbox."
        ));

        // 2. Check Client ID & Secret can fetch an OAuth token live
        String appId = null;
        boolean tokenOk = false;
        try {
            PayPalAuthService.PayPalTokenMetadata metadata = authService.verifyCredentialsAndGetMetadata();
            appId = metadata.appId();
            tokenOk = true;
            checks.add(new DiagnosticCheckDto(
                "oauth_token",
                "PayPal Client Credentials & OAuth Token",
                "PASS",
                true,
                "Successfully fetched Sandbox OAuth token" + (appId != null ? " (App ID: " + appId + ")" : "") + ".",
                null
            ));
        } catch (PayPalApiException e) {
            log.warn("Diagnostics OAuth token check failed with status {}", e.getStatusCode());
            checks.add(new DiagnosticCheckDto(
                "oauth_token",
                "PayPal Client Credentials & OAuth Token",
                "FAIL",
                false,
                "Failed to fetch OAuth token from PayPal (HTTP " + e.getStatusCode() + ").",
                "Check PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET in .env or Render Dashboard under PayPal Developer -> Apps & Credentials -> Sandbox."
            ));
        }

        // 3. Check PAYPAL_WEBHOOK_ID is configured
        String configuredWebhookId = properties.webhookId() != null ? properties.webhookId().trim() : "";
        boolean webhookIdSetOk = !configuredWebhookId.isBlank()
            && !"your_paypal_sandbox_webhook_id".equalsIgnoreCase(configuredWebhookId);
        checks.add(new DiagnosticCheckDto(
            "webhook_id_set",
            "PAYPAL_WEBHOOK_ID Configured",
            webhookIdSetOk ? "PASS" : "FAIL",
            webhookIdSetOk,
            webhookIdSetOk
                ? "PAYPAL_WEBHOOK_ID is configured for signature verification (value redacted)."
                : "PAYPAL_WEBHOOK_ID is missing or still set to a placeholder.",
            webhookIdSetOk
                ? null
                : "Create a webhook in PayPal Developer Dashboard -> Sandbox App -> Webhooks, copy its Webhook ID, and set PAYPAL_WEBHOOK_ID."
        ));

        // 4. Check registered webhooks via GET /v1/notifications/webhooks
        boolean webhookRegisteredOk = checkRegisteredWebhook(checks, tokenOk, expectedWebhookUrl, configuredWebhookId, webhookIdSetOk);

        // 5. Check last webhook received timestamp
        Optional<Instant> lastWebhookOpt = lastWebhookTimeProvider.findLastWebhookReceivedAt();
        Instant lastWebhookAt = lastWebhookOpt.orElse(null);
        checks.add(new DiagnosticCheckDto(
            "last_webhook_received",
            "Last Webhook Received",
            lastWebhookAt != null ? "PASS" : "WARN",
            lastWebhookAt != null,
            lastWebhookAt != null
                ? "Most recent webhook arrived at " + lastWebhookAt + "."
                : "No webhooks have been received in webhook_events yet.",
            lastWebhookAt != null
                ? null
                : "Complete a Demo shop PayPal payment or trigger a PayPal Webhook Simulator event to " + expectedWebhookUrl + "."
        ));

        // 6. Identify Sandbox business account if exposed by PayPal API
        String businessAccount = resolveSandboxBusinessAccount(checks, tokenOk, appId);

        boolean overallOk = sandboxUrlOk && tokenOk && webhookIdSetOk && webhookRegisteredOk;

        return new PayPalDiagnosticsDto(
            overallOk,
            clock.instant(),
            normalizedBaseUrl,
            expectedWebhookUrl,
            appId,
            businessAccount,
            lastWebhookAt,
            List.copyOf(checks)
        );
    }

    private boolean checkRegisteredWebhook(
        List<DiagnosticCheckDto> checks,
        boolean tokenOk,
        String expectedWebhookUrl,
        String configuredWebhookId,
        boolean webhookIdSetOk
    ) {
        if (!tokenOk) {
            checks.add(new DiagnosticCheckDto(
                "webhook_registered",
                "Registered Webhook URL & Events",
                "FAIL",
                false,
                "Skipped because PayPal OAuth token exchange failed.",
                "Fix PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET first so PayResolve can inspect registered webhooks."
            ));
            return false;
        }

        try {
            List<RegisteredWebhook> webhooks = webhookManagementClient.listWebhooks();
            Optional<RegisteredWebhook> matchingWebhook = webhooks.stream()
                .filter(w -> expectedWebhookUrl.equals(stripTrailingSlash(w.url())))
                .findFirst();

            if (matchingWebhook.isEmpty()) {
                String existingUrls = webhooks.isEmpty()
                    ? "none registered"
                    : webhooks.stream().map(RegisteredWebhook::url).collect(Collectors.joining(", "));
                String localhostNote = expectedWebhookUrl.startsWith("http://localhost") || expectedWebhookUrl.startsWith("http://127.0.0.1")
                    ? " APP_PUBLIC_URL points to localhost, which PayPal cannot reach without an HTTPS tunnel (e.g. Cloudflare Tunnel) or Render deploy."
                    : "";
                checks.add(new DiagnosticCheckDto(
                    "webhook_registered",
                    "Registered Webhook URL & Events",
                    "FAIL",
                    false,
                    "No PayPal webhook found matching " + expectedWebhookUrl + " (registered: " + existingUrls + ").",
                    "In PayPal Developer Dashboard -> Sandbox App -> Webhooks, register " + expectedWebhookUrl
                        + " and subscribe to PAYMENT.CAPTURE.COMPLETED, PAYMENT.CAPTURE.REFUNDED, and CHECKOUT.ORDER.APPROVED."
                        + localhostNote
                ));
                return false;
            }

            RegisteredWebhook matched = matchingWebhook.get();
            Set<String> subscribedNames = matched.eventTypes().stream()
                .map(WebhookEventType::name)
                .filter(n -> n != null && !n.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

            boolean wildcard = subscribedNames.contains("*");
            List<String> missingEvents = wildcard
                ? List.of()
                : REQUIRED_WEBHOOK_EVENTS.stream()
                    .filter(req -> !subscribedNames.contains(req))
                    .toList();

            if (!missingEvents.isEmpty()) {
                checks.add(new DiagnosticCheckDto(
                    "webhook_registered",
                    "Registered Webhook URL & Events",
                    "FAIL",
                    false,
                    "Webhook URL matches " + expectedWebhookUrl + ", but is missing required events: " + String.join(", ", missingEvents) + ".",
                    "Edit the webhook in PayPal Developer Dashboard -> Sandbox App -> Webhooks to include: " + String.join(", ", missingEvents) + "."
                ));
                return false;
            }

            if (webhookIdSetOk && matched.id() != null && !matched.id().equals(configuredWebhookId)) {
                checks.add(new DiagnosticCheckDto(
                    "webhook_registered",
                    "Registered Webhook URL & Events",
                    "FAIL",
                    false,
                    "Webhook URL and events match " + expectedWebhookUrl + ", but PAYPAL_WEBHOOK_ID does not match this registration's ID (signature verification will fail).",
                    "Copy the Webhook ID shown next to " + expectedWebhookUrl + " in PayPal Developer Dashboard and update PAYPAL_WEBHOOK_ID."
                ));
                return false;
            }

            String eventSummary = wildcard ? "all events (*)" : String.join(", ", subscribedNames);
            checks.add(new DiagnosticCheckDto(
                "webhook_registered",
                "Registered Webhook URL & Events",
                "PASS",
                true,
                "Matched webhook for " + expectedWebhookUrl + " covering: " + eventSummary + ".",
                null
            ));
            return true;
        } catch (PayPalApiException e) {
            log.warn("Diagnostics webhook listing failed with status {}", e.getStatusCode());
            checks.add(new DiagnosticCheckDto(
                "webhook_registered",
                "Registered Webhook URL & Events",
                "FAIL",
                false,
                "Could not list registered PayPal webhooks (HTTP " + e.getStatusCode() + ").",
                "Check network connectivity and ensure the REST app credentials have webhook read permissions."
            ));
            return false;
        }
    }

    private String resolveSandboxBusinessAccount(List<DiagnosticCheckDto> checks, boolean tokenOk, String appId) {
        if (!tokenOk) {
            checks.add(new DiagnosticCheckDto(
                "sandbox_business_account",
                "Sandbox Business Account",
                "WARN",
                false,
                "Cannot determine linked Sandbox business account without a valid OAuth token.",
                "Fix PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET first."
            ));
            return null;
        }

        Optional<PayPalUserInfoResponse> userInfoOpt = webhookManagementClient.fetchUserInfoIfExposed();
        if (userInfoOpt.isPresent()) {
            PayPalUserInfoResponse info = userInfoOpt.get();
            Optional<String> emailOpt = info.primaryEmail();
            String payerId = info.payerId();
            String accountLabel = emailOpt.orElse(payerId != null && !payerId.isBlank() ? payerId : info.name());
            if (accountLabel != null && !accountLabel.isBlank()) {
                checks.add(new DiagnosticCheckDto(
                    "sandbox_business_account",
                    "Sandbox Business Account",
                    "PASS",
                    true,
                    "Sandbox Business Account: " + accountLabel
                        + (payerId != null && emailOpt.isPresent() ? " (Payer ID: " + payerId + ")" : "")
                        + (appId != null ? " · App ID: " + appId : "") + ".",
                    null
                ));
                return accountLabel;
            }
        }

        String appNote = appId != null ? "App ID: " + appId + ". " : "";
        checks.add(new DiagnosticCheckDto(
            "sandbox_business_account",
            "Sandbox Business Account",
            "INFO",
            true,
            appNote + "PayPal client_credentials OAuth tokens do not expose the owner business account email via API.",
            "Check developer.paypal.com -> My Apps & Credentials -> click your Sandbox REST app to see which Sandbox Business account it belongs to."
        ));
        return appId != null ? "App " + appId + " (email not exposed by client_credentials)" : null;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

