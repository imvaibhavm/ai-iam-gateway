package com.aiguard.ai.gateway.guard.policy;

import java.util.Map;

public record PolicyObligation(Type type, Map<String, String> parameters) {
    public PolicyObligation {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
    public static PolicyObligation of(Type type) { return new PolicyObligation(type, Map.of()); }

    public enum Type {
        MASK_INPUT,
        INSPECT_OUTPUT,
        REQUIRE_LOCAL_MODEL,
        REQUIRE_APPROVAL,
        RECORD_AUDIT,
        LIMIT_COST
    }
}
