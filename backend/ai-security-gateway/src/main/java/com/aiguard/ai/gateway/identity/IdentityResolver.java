package com.aiguard.ai.gateway.identity;

import com.aiguard.ai.gateway.iam.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class IdentityResolver {
    public IdentityContext require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Validated JWT principal required");
        }
        String email = claim(jwt, "email", jwt.getSubject()).trim().toLowerCase();
        String tenant = claim(jwt, "tenant_id", "default").trim().toLowerCase();
        String roleClaim = claim(jwt, "role", "INTERN");
        UserRole role;
        try { role = UserRole.valueOf(roleClaim.toUpperCase()); }
        catch (Exception ignored) { role = UserRole.INTERN; }
        IdentityType type = enumClaim(jwt, "actor_type", IdentityType.HUMAN);
        String delegatedBy = claim(jwt, "delegated_by", null);
        Set<String> scopes = scopes(jwt);
        Map<String, String> attributes = new HashMap<>();
        copyClaim(jwt, attributes, "department");
        copyClaim(jwt, attributes, "region");
        copyClaim(jwt, attributes, "clearance");
        return new IdentityContext(jwt.getSubject(), email, tenant, role, type, delegatedBy, scopes, attributes);
    }

    private String claim(Jwt jwt, String name, String fallback) {
        String value = jwt.getClaimAsString(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private IdentityType enumClaim(Jwt jwt, String name, IdentityType fallback) {
        try { return IdentityType.valueOf(claim(jwt, name, fallback.name()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private Set<String> scopes(Jwt jwt) {
        Set<String> result = new HashSet<>();
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) result.addAll(Arrays.asList(scope.split("\\s+")));
        List<String> scp = jwt.getClaimAsStringList("scp");
        if (scp != null) result.addAll(scp);
        result.removeIf(String::isBlank);
        return result;
    }

    private void copyClaim(Jwt jwt, Map<String, String> target, String name) {
        String value = jwt.getClaimAsString(name);
        if (value != null && !value.isBlank()) target.put(name, value);
    }
}
