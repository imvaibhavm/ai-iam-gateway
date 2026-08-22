package com.aiguard.ai.gateway.guard.policy;

public record PolicyDecision(
        Effect effect,
        Risk risk,
        String policyVersion,
        String reason,
        java.util.List<PolicyObligation> obligations
) {
    public PolicyDecision {
        effect = effect == null ? Effect.DENY : effect;
        risk = risk == null ? Risk.HIGH : risk;
        policyVersion = policyVersion == null || policyVersion.isBlank() ? "unknown" : policyVersion;
        obligations = obligations == null ? java.util.List.of() : java.util.List.copyOf(obligations);
    }
    public PolicyDecision(boolean allowed, String reason, java.util.List<PolicyObligation> obligations) {
        this(allowed ? Effect.ALLOW : Effect.DENY, allowed ? Risk.MEDIUM : Risk.HIGH,
                "unknown", reason, obligations);
    }
    public PolicyDecision(boolean allowed, String reason) { this(allowed, reason, java.util.List.of()); }
    public boolean allowed() { return effect == Effect.ALLOW; }
    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(true, reason);
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason);
    }

    public static PolicyDecision allow(String reason, PolicyObligation... obligations) {
        return new PolicyDecision(true, reason, java.util.List.of(obligations));
    }

    public static PolicyDecision allow(String version, Risk risk, String reason,
                                       java.util.List<PolicyObligation> obligations) {
        return new PolicyDecision(Effect.ALLOW, risk, version, reason, obligations);
    }

    public static PolicyDecision deny(String version, Risk risk, String reason) {
        return new PolicyDecision(Effect.DENY, risk, version, reason, java.util.List.of());
    }

    public enum Effect { ALLOW, DENY, REQUIRE_APPROVAL }
    public enum Risk { LOW, MEDIUM, HIGH, CRITICAL }
}
