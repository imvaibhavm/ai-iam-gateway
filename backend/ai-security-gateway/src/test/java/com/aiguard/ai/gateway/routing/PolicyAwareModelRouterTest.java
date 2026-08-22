package com.aiguard.ai.gateway.routing;

import com.aiguard.ai.gateway.guard.intent.IntentType;
import com.aiguard.ai.gateway.guard.policy.PolicyDecision;
import com.aiguard.ai.gateway.guard.policy.PolicyObligation;
import com.aiguard.ai.gateway.provider.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class PolicyAwareModelRouterTest {
    @Test void sensitiveDataNeverRoutesToCloud() {
        var router = new PolicyAwareModelRouter(new ProviderRegistry(List.of(provider("openai", true), provider("ollama", false))), "openai", true);
        var decision = router.select(IntentType.PII);
        assertEquals("ollama", decision.selected().providerId());
        assertTrue(decision.fallbacks().stream().noneMatch(ModelProvider::cloud));
    }
    @Test void preferredHealthyProviderIsSelected() {
        var router = new PolicyAwareModelRouter(new ProviderRegistry(List.of(provider("openai", true), provider("ollama", false))), "openai", true);
        assertEquals("openai", router.select(IntentType.GENERAL).selected().providerId());
    }
    @Test void localModelObligationOverridesPreferredCloudProvider() {
        var router = new PolicyAwareModelRouter(new ProviderRegistry(List.of(provider("openai", true), provider("ollama", false))), "openai", true);
        var policy = PolicyDecision.allow("restricted", PolicyObligation.of(PolicyObligation.Type.REQUIRE_LOCAL_MODEL));
        assertEquals("ollama", router.select(IntentType.GENERAL, policy).selected().providerId());
    }
    private ModelProvider provider(String id, boolean cloud) {
        return new ModelProvider() {
            public String providerId() { return id; } public String modelId() { return "test"; }
            public boolean cloud() { return cloud; } public ProviderHealth health() { return ProviderHealth.up(); }
            public ModelResponse generate(ModelRequest r) { return null; }
            public ModelResponse stream(ModelRequest r, Consumer<String> c) { return null; }
        };
    }
}
