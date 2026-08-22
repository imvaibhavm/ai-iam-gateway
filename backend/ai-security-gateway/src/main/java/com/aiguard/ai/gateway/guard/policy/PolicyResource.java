package com.aiguard.ai.gateway.guard.policy;

import java.util.Map;

public record PolicyResource(String type, String id, String tenantId, String ownerSubject,
                             Map<String, String> attributes) {
    public PolicyResource {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static PolicyResource tenantResource(String type, String id, String tenantId) {
        return new PolicyResource(type, id, tenantId, null, Map.of());
    }
}
