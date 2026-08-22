package com.aiguard.ai.gateway.guard.policy;

import com.aiguard.ai.gateway.identity.IdentityContext;
import org.springframework.stereotype.Component;

/** Safe baseline ReBAC policy. Replace with a graph-backed implementation as relationships mature. */
@Component
public class TenantRelationshipAuthorizer implements RelationshipAuthorizer {
    @Override
    public boolean canAccess(IdentityContext identity, String action, PolicyResource resource) {
        if (resource == null) return true;
        if (!identity.tenantId().equals(resource.tenantId())) return false;
        if (resource.ownerSubject() == null || resource.ownerSubject().isBlank()) return true;
        if (identity.subject().equals(resource.ownerSubject())) return true;
        String relationship = identity.attributes().get("relationship:" + resource.id());
        return "owner".equals(relationship) || "editor".equals(relationship)
                || ("read".equals(action) && "viewer".equals(relationship));
    }
}
