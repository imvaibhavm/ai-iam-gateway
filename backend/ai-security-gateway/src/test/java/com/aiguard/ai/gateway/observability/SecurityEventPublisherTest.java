package com.aiguard.ai.gateway.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SecurityEventPublisherTest {
    @Test
    void publishesContentFreeEventAndRecordsMetrics() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        InMemorySecurityEventExporter recent = new InMemorySecurityEventExporter(10);
        SecurityEventPublisher publisher = new SecurityEventPublisher(
                List.of(recent), meters, ObservationRegistry.create());

        publisher.publish(event("tenant-a", true, true));

        assertThat(recent.recentForTenant("tenant-a", 10)).hasSize(1);
        assertThat(meters.counter("ai.security.decisions", "outcome", "allowed", "intent", "GENERAL").count())
                .isEqualTo(1);
        assertThat(meters.counter("ai.model.invocations", "provider", "ollama", "outcome", "success").count())
                .isEqualTo(1);
        assertThat(meters.counter("ai.security.output.redactions").count()).isEqualTo(1);
    }

    @Test
    void exporterFailureDoesNotBreakTheGateway() {
        SecurityEventExporter failing = new SecurityEventExporter() {
            @Override public String exporterId() { return "broken"; }
            @Override public void export(SecurityEvent event) { throw new IllegalStateException("offline"); }
        };
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        SecurityEventPublisher publisher = new SecurityEventPublisher(
                List.of(failing), meters, ObservationRegistry.create());

        assertThatCode(() -> publisher.publish(event("tenant-a", false, false))).doesNotThrowAnyException();
        assertThat(meters.counter("ai.security.event.exports", "exporter", "broken", "outcome", "failure").count())
                .isEqualTo(1);
    }

    private SecurityEvent event(String tenant, boolean allowed, boolean redacted) {
        return new SecurityEvent("1.0", "ai.policy.decision", Instant.now(), "audit-1", "request-1",
                tenant, "user@example.com", "ENGINEER", "GENERAL", allowed, "policy", "v1",
                "ollama", "llama", "local", true, redacted, "", 12, 4, 8, 0);
    }
}
