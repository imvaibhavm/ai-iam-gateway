package com.aiguard.ai.gateway.identity;

import org.springframework.stereotype.Component;

@Component
public class DelegationPolicy {
    public Result evaluate(IdentityContext identity, String action) {
        if (identity.type() == IdentityType.HUMAN) return Result.allow();
        if (!identity.delegated()) return Result.denied("non_human_identity_requires_delegation");
        if (identity.delegatedBy().equals(identity.subject())) return Result.denied("self_delegation_forbidden");
        if (!identity.scopes().contains(action) && !identity.scopes().contains("*")) {
            return Result.denied("delegation_scope_missing");
        }
        return Result.allow();
    }

    public record Result(boolean allowed, String reason) {
        static Result allow() { return new Result(true, "delegation_valid"); }
        static Result denied(String reason) { return new Result(false, reason); }
    }
}
