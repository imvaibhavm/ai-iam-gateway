package com.aiguard.ai.gateway.provider;

import com.aiguard.ai.gateway.routing.RoutingDecision;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ProviderExecutorTest {
    @Test void fallsBackOnlyToRouterApprovedProvider() {
        ModelProvider failed = provider("primary", true, true);
        ModelProvider fallback = provider("fallback", false, false);
        var result = new ProviderExecutor().execute(new RoutingDecision(failed, "test", List.of(fallback)),
                new ModelRequest("r1", "hello", 10, java.util.Map.of()), false, token -> {});
        assertTrue(result.fallbackUsed());
        assertEquals("fallback", result.response().provider());
    }
    private ModelProvider provider(String id, boolean cloud, boolean fails) {
        return new ModelProvider() {
            public String providerId() { return id; } public String modelId() { return "model"; }
            public boolean cloud() { return cloud; } public ProviderHealth health() { return ProviderHealth.up(); }
            public ModelResponse generate(ModelRequest r) { if (fails) throw new IllegalStateException("down"); return new ModelResponse("ok", id, "model", 1, 1, 1, 0); }
            public ModelResponse stream(ModelRequest r, Consumer<String> c) { return generate(r); }
        };
    }
}
