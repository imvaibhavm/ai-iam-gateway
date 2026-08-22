package com.aiguard.ai.gateway.identity;

import com.aiguard.ai.gateway.iam.UserRole;
import java.util.Map;
import java.util.Set;

public record IdentityContext(
        String subject,
        String email,
        String tenantId,
        UserRole role,
        IdentityType type,
        String delegatedBy,
        Set<String> scopes,
        Set<String> groups,
        Map<String, String> attributes
) {
    public IdentityContext {
        type = type == null ? IdentityType.HUMAN : type;
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        groups = groups == null ? Set.of() : Set.copyOf(groups);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public IdentityContext(String subject, String email, String tenantId, UserRole role) {
        this(subject, email, tenantId, role, IdentityType.HUMAN, null, Set.of(), Set.of(), Map.of());
    }

    public IdentityContext(String subject, String email, String tenantId, UserRole role, IdentityType type,
                           String delegatedBy, Set<String> scopes, Map<String, String> attributes) {
        this(subject, email, tenantId, role, type, delegatedBy, scopes, Set.of(), attributes);
    }

    public boolean delegated() { return delegatedBy != null && !delegatedBy.isBlank(); }

    public IdentityContext withRole(UserRole effectiveRole) {
        return new IdentityContext(subject, email, tenantId, effectiveRole, type, delegatedBy, scopes, groups, attributes);
    }
}
