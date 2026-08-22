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
        DISABLE_MEMORY,
        LIMIT_OUTPUT_TOKENS,
        MAX_COST_USD,
        LIMIT_COST
    }

    public static PolicyObligation outputTokenLimit(int value) {
        return new PolicyObligation(Type.LIMIT_OUTPUT_TOKENS, Map.of("tokens", Integer.toString(value)));
    }
    public static PolicyObligation maxCostUsd(String value) {
        return new PolicyObligation(Type.MAX_COST_USD, Map.of("usd", value));
    }
}
