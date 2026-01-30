package com.aiguard.ai.gateway.guard.policy;

public record PolicyDecision(
        boolean allowed,
        String reason
) {
    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(true, reason);
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason);
    }
}
