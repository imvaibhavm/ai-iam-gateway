package com.aiguard.ai.gateway.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/** Central, content-free AI telemetry. Values must be identifiers or classifications, never prompts. */
@Component
public class AiTelemetry {
    private final ObservationRegistry registry;

    public AiTelemetry(ObservationRegistry registry) { this.registry = registry; }

    public <T> T observe(String span, Map<String, ?> safeAttributes, Supplier<T> operation) {
        Observation observation = observation(span, safeAttributes).start();
        try (Observation.Scope ignored = observation.openScope()) {
            T result = operation.get();
            observation.lowCardinalityKeyValue("outcome", "success");
            return result;
        } catch (RuntimeException error) {
            observation.error(error).lowCardinalityKeyValue("outcome", "failure");
            throw error;
        } finally {
            observation.stop();
        }
    }

    public void observe(String span, Map<String, ?> safeAttributes, Runnable operation) {
        observe(span, safeAttributes, () -> { operation.run(); return null; });
    }

    public Observation observation(String span, Map<String, ?> safeAttributes) {
        Observation result = Observation.createNotStarted(span, registry);
        safeAttributes.forEach((key, value) -> {
            if (value != null) result.lowCardinalityKeyValue(key, sanitize(value));
        });
        return result;
    }

    private String sanitize(Object value) {
        String text = String.valueOf(value).replaceAll("[\\r\\n\\t]", "_");
        return text.length() > 128 ? text.substring(0, 128) : text;
    }
}
