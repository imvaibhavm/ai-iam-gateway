package com.aiguard.ai.gateway.guard.policy;

import com.aiguard.ai.gateway.identity.IdentityContext;

public interface RelationshipAuthorizer {
    boolean canAccess(IdentityContext identity, String action, PolicyResource resource);
}
