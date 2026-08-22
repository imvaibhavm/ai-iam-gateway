package com.aiguard.ai.gateway.guard.policy;

import com.aiguard.ai.gateway.guard.intent.IntentClassification;
import com.aiguard.ai.gateway.identity.IdentityContext;
import java.math.BigDecimal;
import java.util.Map;

public record PolicyContext(
        IdentityContext identity,
        IntentClassification intent,
        String action,
        PolicyResource resource,
        DataClassification dataClassification,
        String requestedRegion,
        BigDecimal estimatedCostUsd,
        Map<String, String> environment
) {
    public PolicyContext {
        dataClassification = dataClassification == null ? DataClassification.INTERNAL : dataClassification;
        estimatedCostUsd = estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd;
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public static PolicyContext llm(IdentityContext identity, IntentClassification intent,
                                    DataClassification classification) {
        return new PolicyContext(identity, intent, "llm.generate", null, classification,
                identity.attributes().get("region"), BigDecimal.ZERO, Map.of());
    }
}
