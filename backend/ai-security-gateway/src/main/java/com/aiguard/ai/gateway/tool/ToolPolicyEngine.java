package com.aiguard.ai.gateway.tool;

import org.springframework.stereotype.Component;

@Component
public class ToolPolicyEngine {
    public Decision evaluate(ToolRequest request, ToolDescriptor descriptor,
                             ArgumentInspector.Inspection inspection) {
        if (!descriptor.allowedRoles().isEmpty() && !descriptor.allowedRoles().contains(request.identity().role()))
            return Decision.deny("role_not_authorized");
        if (!descriptor.allowedTenants().isEmpty() && !descriptor.allowedTenants().contains(request.identity().tenantId()))
            return Decision.deny("tenant_not_authorized");
        if (descriptor.tenantAware()) {
            Object resourceTenant = request.arguments().get("tenantId");
            if (resourceTenant != null && !request.identity().tenantId().equals(String.valueOf(resourceTenant)))
                return Decision.deny("cross_tenant_tool_access");
        }
        if (!descriptor.requiredScopes().isEmpty() && !request.identity().scopes().contains("*")
                && !request.identity().scopes().containsAll(descriptor.requiredScopes()))
            return Decision.deny("delegation_scope_missing");
        if (inspection.secret()) return Decision.deny("secret_in_tool_arguments");
        if (inspection.sensitive() && !descriptor.piiAllowed()) return Decision.deny("pii_not_allowed_for_tool");
        if (descriptor.approvalRequired() || descriptor.riskLevel() == ToolRisk.HIGH
                || descriptor.riskLevel() == ToolRisk.CRITICAL || inspection.sensitive())
            return Decision.approval("human_approval_required");
        return Decision.allow();
    }

    public record Decision(boolean allowed, boolean approvalRequired, String reason) {
        public static Decision allow() { return new Decision(true, false, "tool_policy_allowed"); }
        public static Decision approval(String reason) { return new Decision(true, true, reason); }
        public static Decision deny(String reason) { return new Decision(false, false, reason); }
    }
}
