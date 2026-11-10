package com.payresolve.ingest;

import com.payresolve.ingest.dto.EventInboxItemDto;
import com.payresolve.ingest.dto.EventStatsDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only query service exposing webhook inbox records across modules.
 */
@Service
public class EventQueryService {

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
}
