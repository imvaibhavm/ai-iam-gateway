package com.aiguard.ai.gateway.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded operational view; the database audit log remains the source of truth. */
@Component
public class InMemorySecurityEventExporter implements SecurityEventExporter {
    private final ArrayDeque<SecurityEvent> events = new ArrayDeque<>();
    private final int capacity;

    public InMemorySecurityEventExporter(@Value("${gateway.observability.recent-event-capacity:500}") int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    @Override public String exporterId() { return "recent-events"; }

    @Override
    public synchronized void export(SecurityEvent event) {
        if (events.size() == capacity) events.removeFirst();
        events.addLast(event);
    }

    public synchronized List<SecurityEvent> recentForTenant(String tenantId, int limit) {
        return events.reversed().stream()
                .filter(event -> tenantId.equals(event.tenantId()))
                .limit(Math.min(Math.max(limit, 1), 500))
                .toList();
    }
}
