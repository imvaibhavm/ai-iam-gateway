package com.aiguard.ai.gateway.identity;

import java.util.Map;
import java.util.Set;

public record ExternalIdentityClaims(
        String issuer,
        String subject,
        String email,
        Set<String> groups,
        Map<String, String> attributes
) {
    public ExternalIdentityClaims {
        groups = groups == null ? Set.of() : Set.copyOf(groups);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
