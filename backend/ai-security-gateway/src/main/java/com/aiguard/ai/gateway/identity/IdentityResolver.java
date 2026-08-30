package com.aiguard.ai.gateway.identity;

import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class IdentityResolver {
    private final ExternalIdentityClaimsMapper externalClaimsMapper;
    private final AppUserService users;
    private final boolean devTokenEnabled;
    private final String devIssuer;

    public IdentityResolver(ExternalIdentityClaimsMapper externalClaimsMapper,
                            AppUserService users,
                            @Value("${security.jwt.dev-token-enabled:false}") boolean devTokenEnabled,
                            @Value("${security.jwt.issuer:ai-security-gateway}") String devIssuer) {
        this.externalClaimsMapper = externalClaimsMapper;
        this.users = users;
        this.devTokenEnabled = devTokenEnabled;
        this.devIssuer = devIssuer;
    }

    public IdentityContext require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Validated JWT principal required");
        }
        if (devTokenEnabled && devIssuer.equals(jwt.getClaimAsString("iss"))) {
            return resolveDevelopment(jwt);
        }

        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        Optional<AppUser> bound = users.findBoundExternal(issuer, jwt.getSubject());
        ExternalIdentityClaims claims = bound.isPresent()
                ? externalClaimsMapper.mapBound(jwt)
                : externalClaimsMapper.map(jwt);
        AppUser user = bound.orElseGet(() -> users.resolveExternal(claims));
        if (!user.isEnabled()) throw new IdentityResolutionException("Local user is disabled");
        Map<String, String> attributes = new HashMap<>(claims.attributes());
        attributes.remove("assertedTenant");
        putAuthoritative(attributes, "department", user.getDepartment());
        putAuthoritative(attributes, "clearance", user.getClearance());
        putAuthoritative(attributes, "region", user.getRegion());
        putAuthoritative(attributes, "policyAssignments", user.getPolicyAssignments());
        return new IdentityContext(claims.subject(), user.getEmail(), user.getTenantId(), user.getRole(),
                IdentityType.HUMAN, null, scopes(jwt), claims.groups(), attributes);
    }

    private IdentityContext resolveDevelopment(Jwt jwt) {
        String email = claim(jwt, "email", jwt.getSubject()).trim().toLowerCase(Locale.ROOT);
        String tenant = claim(jwt, "tenant_id", "default").trim().toLowerCase(Locale.ROOT);
        String roleClaim = claim(jwt, "role", "INTERN");
        UserRole assertedRole;
        try { assertedRole = UserRole.valueOf(roleClaim.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { assertedRole = UserRole.INTERN; }
        AppUser user = users.getOrCreate(tenant, email, assertedRole);
        if (!user.isEnabled()) throw new IdentityResolutionException("Local user is disabled");
        IdentityType type = enumClaim(jwt, "actor_type", IdentityType.HUMAN);
        String delegatedBy = claim(jwt, "delegated_by", null);
        Map<String, String> attributes = new HashMap<>();
        copyClaim(jwt, attributes, "department");
        copyClaim(jwt, attributes, "region");
        copyClaim(jwt, attributes, "clearance");
        return new IdentityContext(jwt.getSubject(), user.getEmail(), user.getTenantId(), user.getRole(), type,
                delegatedBy, scopes(jwt), Set.of(), attributes);
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

    private void putAuthoritative(Map<String, String> target, String name, String value) {
        if (value != null && !value.isBlank()) target.put(name, value);
    }
}
