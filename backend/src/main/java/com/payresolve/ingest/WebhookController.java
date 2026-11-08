package com.payresolve.ingest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller receiving incoming PayPal webhook notifications.
 * Fast ingestion: persists raw payload, verifies signature, returns 200 immediately.
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
    @Operation(summary = "Ingest PayPal webhook notification")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers
    ) {
        log.info("Received incoming PayPal webhook notification");
        IngestResult result = ingestService.ingest(rawBody, headers);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.status());
        response.put("paypalEventId", result.paypalEventId() != null ? result.paypalEventId() : "");
        response.put("verified", result.verified());
        response.put("duplicate", result.duplicate());
        response.put("dropped", result.dropped());
        response.put("eventId", result.id() != null ? result.id() : 0);

        return ResponseEntity.ok(response);
    }
}
