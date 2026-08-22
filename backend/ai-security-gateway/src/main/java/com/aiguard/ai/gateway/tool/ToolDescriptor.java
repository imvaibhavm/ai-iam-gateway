package com.aiguard.ai.gateway.tool;

import com.aiguard.ai.gateway.iam.UserRole;

import java.util.Set;

public record ToolDescriptor(String name, String description, ToolAction actionType, ToolRisk riskLevel,
                             Set<String> requiredScopes, Set<UserRole> allowedRoles, Set<String> allowedTenants,
                             boolean tenantAware, boolean approvalRequired, boolean piiAllowed) {
    public ToolDescriptor {
        requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
        allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
        allowedTenants = allowedTenants == null ? Set.of() : Set.copyOf(allowedTenants);
    }

    public ToolDescriptor(String name, Set<UserRole> roles, Set<String> tenants,
                          boolean approvalRequired, boolean piiAllowed) {
        this(name, name, ToolAction.READ, approvalRequired ? ToolRisk.HIGH : ToolRisk.LOW,
                Set.of(), roles, tenants, true, approvalRequired, piiAllowed);
    }
}
