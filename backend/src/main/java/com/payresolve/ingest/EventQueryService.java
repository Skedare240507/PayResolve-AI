package com.payresolve.ingest;

import com.payresolve.ingest.dto.EventInboxItemDto;
import com.payresolve.ingest.dto.EventStatsDto;
import com.payresolve.ingest.dto.OrderWebhookTraceDto;
import com.payresolve.paypal.LastWebhookTimeProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only query service exposing webhook inbox records across modules.
 */
@Service
public class EventQueryService implements LastWebhookTimeProvider {

    private final WebhookEventRepository repository;

    public EventQueryService(WebhookEventRepository repository) {
        this.repository = repository;
    }

    public List<EventInboxItemDto> getRecentEvents(int limit) {
        return repository.findAll(limit, 0).stream()
            .map(EventInboxItemDto::from)
            .toList();
    }

    public EventStatsDto getStats() {
        return repository.getStats();
    }

    @Override
    public Optional<Instant> findLastWebhookReceivedAt() {
        return repository.findLastReceivedAt();
    }

    public List<OrderWebhookTraceDto> findEventsForOrder(String shopOrderId, String paypalOrderId, String captureId) {
        return repository.findByOrderRefs(shopOrderId, paypalOrderId, captureId).stream()
            .map(OrderWebhookTraceDto::from)
            .toList();
    }

    public long countVerifiedDuplicateDeliveriesBlocked() {
        return repository.countVerifiedDuplicateDeliveriesBlocked();
    }
}
