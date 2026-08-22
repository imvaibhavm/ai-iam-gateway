package com.aiguard.ai.gateway.observability;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySecurityEventExporterTest {
    @Test
    void enforcesCapacityAndTenantIsolation() {
        InMemorySecurityEventExporter exporter = new InMemorySecurityEventExporter(2);
        exporter.export(event("old", "tenant-a"));
        exporter.export(event("other", "tenant-b"));
        exporter.export(event("new", "tenant-a"));

        assertThat(exporter.recentForTenant("tenant-a", 10))
                .extracting(SecurityEvent::requestId)
                .containsExactly("new");
        assertThat(exporter.recentForTenant("tenant-b", 10))
                .extracting(SecurityEvent::requestId)
                .containsExactly("other");
    }

    private SecurityEvent event(String requestId, String tenant) {
        return new SecurityEvent("1.0", "ai.policy.decision", Instant.now(), "audit", requestId, tenant,
                "actor", "ADMIN", "GENERAL", true, "allowed", "v1", null, null, null,
                false, false, "", 0, 0, 0, 0);
    }
}
