package com.aiguard.ai.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SecurityEventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityEventPublisher.class);
    private final List<SecurityEventExporter> exporters;
    private final MeterRegistry meters;
    private final ObservationRegistry observations;

    public SecurityEventPublisher(List<SecurityEventExporter> exporters, MeterRegistry meters,
                                  ObservationRegistry observations) {
        this.exporters = List.copyOf(exporters);
        this.meters = meters;
        this.observations = observations;
    }

    public void publish(SecurityEvent event) {
        String outcome = event.allowed() ? "allowed" : "denied";
        String intent = valueOrUnknown(event.intent());
        Observation.createNotStarted("ai.security.event.publish", observations)
                .lowCardinalityKeyValue("outcome", outcome)
                .lowCardinalityKeyValue("intent", intent)
                .observe(() -> exporters.forEach(exporter -> exportSafely(exporter, event)));

        meters.counter("ai.security.decisions", "outcome", outcome, "intent", intent).increment();
        if (event.outputRedacted()) meters.counter("ai.security.output.redactions").increment();
        if (event.provider() != null) {
            meters.counter("ai.model.invocations", "provider", event.provider(),
                    "outcome", event.providerSucceeded() ? "success" : "failure").increment();
            meters.summary("ai.model.latency", "provider", event.provider()).record(event.latencyMs());
            meters.counter("ai.model.tokens", "provider", event.provider(), "direction", "input")
                    .increment(event.inputTokens());
            meters.counter("ai.model.tokens", "provider", event.provider(), "direction", "output")
                    .increment(event.outputTokens());
            meters.counter("ai.model.estimated.cost.usd", "provider", event.provider())
                    .increment(Math.max(0, event.estimatedCostUsd()));
        }
    }

    private void exportSafely(SecurityEventExporter exporter, SecurityEvent event) {
        try {
            exporter.export(event);
            meters.counter("ai.security.event.exports", "exporter", exporter.exporterId(), "outcome", "success")
                    .increment();
        } catch (RuntimeException exception) {
            meters.counter("ai.security.event.exports", "exporter", exporter.exporterId(), "outcome", "failure")
                    .increment();
            LOG.warn("Security event exporter {} failed for request {}", exporter.exporterId(),
                    event.requestId(), exception);
        }
    }

    private String valueOrUnknown(String value) { return value == null || value.isBlank() ? "unknown" : value; }
}
