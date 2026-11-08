package com.payresolve.ingest;

import com.payresolve.common.ResourceNotFoundException;
import com.payresolve.ingest.dto.EventInboxItemDto;
import com.payresolve.ingest.dto.EventStatsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing the durable PayPal Event Inbox and manual replay actions.
 */
@RestController
@RequestMapping("/events")
@Tag(name = "Event Inbox", description = "Query and replay raw PayPal webhook events")
public class EventInboxController {

    private final WebhookEventRepository repository;
    private final WebhookWorkerService workerService;

    public EventInboxController(WebhookEventRepository repository, WebhookWorkerService workerService) {
        this.repository = repository;
        this.workerService = workerService;
    }

    @GetMapping
    @Operation(summary = "List recent webhook events for the inbox")
    public List<EventInboxItemDto> getEvents(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return repository.findAll(limit, offset).stream()
                .map(EventInboxItemDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single webhook event details by internal ID or PayPal Event ID")
    public EventInboxItemDto getEventById(@PathVariable String id) {
        WebhookEvent event = findEventByIdOrPaypalId(id);
        return EventInboxItemDto.from(event);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get aggregate statistics for the event inbox")
    public EventStatsDto getStats() {
        return repository.getStats();
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay event processing synchronously")
    public ResponseEntity<EventInboxItemDto> replayEvent(@PathVariable String id) {
        WebhookEvent event = findEventByIdOrPaypalId(id);
        workerService.processSingleEvent(event.id());

        WebhookEvent refreshed = repository.findById(event.id())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found after replay: " + id));

        return ResponseEntity.ok(EventInboxItemDto.from(refreshed));
    }

    private WebhookEvent findEventByIdOrPaypalId(String id) {
        WebhookEvent event = null;
        try {
            Long numericId = Long.parseLong(id);
            event = repository.findById(numericId).orElse(null);
        } catch (NumberFormatException ignored) {
            // Not numeric, lookup by paypal_event_id below
        }

        if (event == null) {
            event = repository.findByPaypalEventId(id).orElse(null);
        }

        if (event == null) {
            throw new ResourceNotFoundException("Webhook event not found with ID: " + id);
        }

        return event;
    }
}
