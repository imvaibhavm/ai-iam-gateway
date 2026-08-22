package com.aiguard.ai.gateway.routing;

import com.aiguard.ai.gateway.guard.intent.IntentType;
import com.aiguard.ai.gateway.guard.policy.PolicyDecision;
import com.aiguard.ai.gateway.guard.policy.PolicyObligation;
import com.aiguard.ai.gateway.provider.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PolicyAwareModelRouter {
    private final ProviderRegistry registry;
    private final List<String> priority;
    private final boolean allowCloudFallback;

    public PolicyAwareModelRouter(ProviderRegistry registry,
            @Value("${gateway.provider-priority:${gateway.preferred-provider:huggingface}}") String providerPriority,
            @Value("${gateway.allow-cloud-fallback:true}") boolean allowCloudFallback) {
        this.registry = registry;
        this.priority = Arrays.stream(providerPriority.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).map(String::toLowerCase).distinct().toList();
        this.allowCloudFallback = allowCloudFallback;
    }

    public RoutingDecision select(IntentType intent) {
        return select(intent, null);
    }

    /** Policy obligations are authoritative; intent checks remain as defense in depth. */
    public RoutingDecision select(IntentType intent, PolicyDecision policy) {
        boolean obligationRequiresLocal = policy != null && policy.obligations().stream()
                .anyMatch(o -> o.type() == PolicyObligation.Type.REQUIRE_LOCAL_MODEL);
        boolean localOnly = obligationRequiresLocal || intent == IntentType.SECRETS;
        List<ModelProvider> eligible = registry.all().stream()
                .filter(p -> !localOnly || !p.cloud())
                .filter(p -> p.health().available())
                .toList();
        if (eligible.isEmpty()) throw new IllegalStateException(localOnly
                ? "Policy requires a healthy local model provider" : "No healthy model provider available");
        List<ModelProvider> ordered = eligible.stream().sorted(Comparator.comparingInt(this::priorityOf)).toList();
        ModelProvider selected = ordered.getFirst();
        List<ModelProvider> fallbacks = ordered.stream().filter(p -> p != selected)
                .filter(p -> allowCloudFallback || !p.cloud()).toList();
        return new RoutingDecision(selected, localOnly ? "policy_obligation_local_only" : "priority_healthy_provider", fallbacks);
    }

    private int priorityOf(ModelProvider provider) {
        int index = priority.indexOf(provider.providerId().toLowerCase());
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
