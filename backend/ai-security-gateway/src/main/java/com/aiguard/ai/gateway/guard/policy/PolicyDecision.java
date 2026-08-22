package com.aiguard.ai.gateway.guard.policy;

public record PolicyDecision(
        boolean allowed,
        String reason,
        java.util.List<PolicyObligation> obligations
) {
    public PolicyDecision {
        obligations = obligations == null ? java.util.List.of() : java.util.List.copyOf(obligations);
    }
    public PolicyDecision(boolean allowed, String reason) { this(allowed, reason, java.util.List.of()); }
    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(true, reason);
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason);
    }

    public static PolicyDecision allow(String reason, PolicyObligation... obligations) {
        return new PolicyDecision(true, reason, java.util.List.of(obligations));
    }
}
