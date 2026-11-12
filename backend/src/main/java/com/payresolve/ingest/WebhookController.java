package com.payresolve.ingest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller receiving incoming PayPal webhook notifications.
 * Fast ingestion: verifies signature, quarantines unverified attempts, and persists verified events.
 */
@RestController
@RequestMapping("/webhooks/paypal")
@Tag(name = "Webhooks", description = "PayPal Webhook Ingestion")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final WebhookIngestService ingestService;

    public WebhookController(WebhookIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    @Operation(
        summary = "Ingest PayPal webhook notification",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Raw PayPal webhook JSON payload verified against transmission signature headers.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", description = "Raw PayPal webhook JSON string")
            )
        )
    )
    public ResponseEntity<?> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request
    ) {
        log.info("Received incoming PayPal webhook notification");
        try {
            IngestResult result = ingestService.ingest(rawBody, headers, request.getRemoteAddr());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", result.status());
            response.put("paypalEventId", result.paypalEventId() != null ? result.paypalEventId() : "");
            response.put("verified", result.verified());
            response.put("duplicate", result.duplicate());
            response.put("dropped", result.dropped());
            response.put("eventId", result.id() != null ? result.id() : 0);

            return ResponseEntity.ok(response);
        } catch (WebhookIngestService.UnverifiedWebhookRateLimitException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
            problem.setTitle("Too Many Requests");
            problem.setType(URI.create("urn:payresolve:rate-limited"));
            problem.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
        }
    }
}
