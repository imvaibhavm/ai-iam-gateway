package com.aiguard.ai.gateway.identity;

import java.time.Instant;
import java.util.*;

/** Immutable server-side delegation chain with monotonic scope reduction. */
public record DelegationChain(String tenantId, String originator, List<Grant> grants) {
    public DelegationChain { grants = grants == null ? List.of() : List.copyOf(grants); }

    public Validation validate(Instant now) {
        Set<String> parent = null;
        String expectedParent = originator;
        for (Grant grant : grants) {
            if (!tenantId.equals(grant.tenantId())) return Validation.deny("cross_tenant_delegation");
            if (!expectedParent.equals(grant.delegatedBy())) return Validation.deny("broken_delegation_chain");
            if (grant.expiresAt()==null || !grant.expiresAt().isAfter(now)) return Validation.deny("delegation_expired");
            if (parent != null && !parent.contains("*") && !parent.containsAll(grant.scopes()))
                return Validation.deny("delegation_privilege_escalation");
            parent=grant.scopes(); expectedParent=grant.subject();
        }
        return Validation.allow(parent == null ? Set.of() : parent);
    }

    public record Grant(String subject,String delegatedBy,String tenantId,Set<String> scopes,Instant expiresAt) {
        public Grant { scopes=scopes==null?Set.of():Set.copyOf(scopes); }
    }
    public record Validation(boolean allowed,String reason,Set<String> effectiveScopes) {
        static Validation allow(Set<String> scopes){return new Validation(true,"delegation_valid",Set.copyOf(scopes));}
        static Validation deny(String reason){return new Validation(false,reason,Set.of());}
    }
}
