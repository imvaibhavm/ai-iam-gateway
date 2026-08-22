package com.aiguard.ai.gateway.provider;

import com.aiguard.ai.gateway.routing.RoutingDecision;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class ProviderExecutor {
    private final Map<String, FailureState> failures = new ConcurrentHashMap<>();

    public ExecutionResult execute(RoutingDecision routing, ModelRequest request, boolean streaming, Consumer<String> sink) {
        List<ModelProvider> candidates = new ArrayList<>();
        candidates.add(routing.selected()); candidates.addAll(routing.fallbacks());
        RuntimeException last = null;
        for (ModelProvider provider : candidates) {
            if (circuitOpen(provider.providerId())) continue;
            try {
                ModelResponse response = streaming ? provider.stream(request, sink) : provider.generate(request);
                failures.remove(provider.providerId());
                return new ExecutionResult(response, provider != routing.selected());
            } catch (RuntimeException ex) {
                recordFailure(provider.providerId()); last = ex;
            }
        }
        throw new IllegalStateException("All policy-eligible providers failed", last);
    }

    private boolean circuitOpen(String id) {
        FailureState state = failures.get(id);
        return state != null && state.count >= 3 && Instant.now().isBefore(state.lastFailure.plusSeconds(60));
    }
    private void recordFailure(String id) {
        failures.compute(id, (key, old) -> new FailureState(old == null ? 1 : old.count + 1, Instant.now()));
    }
    public record ExecutionResult(ModelResponse response, boolean fallbackUsed) { }
    private record FailureState(int count, Instant lastFailure) { }
}
