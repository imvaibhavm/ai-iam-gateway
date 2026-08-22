package com.aiguard.ai.gateway.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConfigurableOidcClaimsMapper implements ExternalIdentityClaimsMapper {
    private final String emailClaim;
    private final String groupsClaim;
    private final Map<String, String> attributeClaims;

    public ConfigurableOidcClaimsMapper(
            @Value("${security.oidc.email-claim:email}") String emailClaim,
            @Value("${security.oidc.groups-claim:groups}") String groupsClaim,
            @Value("${security.oidc.tenant-claim:https://aiguard.example/tenant_id}") String tenantClaim,
            @Value("${security.oidc.department-claim:https://aiguard.example/department}") String departmentClaim,
            @Value("${security.oidc.clearance-claim:https://aiguard.example/clearance}") String clearanceClaim) {
        this.emailClaim = emailClaim;
        this.groupsClaim = groupsClaim;
        this.attributeClaims = Map.of(
                "assertedTenant", tenantClaim,
                "department", departmentClaim,
                "clearance", clearanceClaim
        );
    }

    @Override
    public ExternalIdentityClaims map(Jwt jwt) {
        String subject = required(jwt.getSubject(), "OIDC subject claim is required");
        String email = required(stringClaim(jwt, emailClaim), "Configured OIDC email claim is required")
                .trim().toLowerCase(Locale.ROOT);
        Map<String, String> attributes = new HashMap<>();
        attributeClaims.forEach((target, source) -> {
            String value = stringClaim(jwt, source);
            if (value != null && !value.isBlank()) attributes.put(target, value.trim());
        });
        return new ExternalIdentityClaims(
                jwt.getIssuer() == null ? null : jwt.getIssuer().toString(),
                subject,
                email,
                collectionClaim(jwt, groupsClaim),
                attributes
        );
    }

    private static String stringClaim(Jwt jwt, String name) {
        if (name == null || name.isBlank()) return null;
        Object value = jwt.getClaims().get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static Set<String> collectionClaim(Jwt jwt, String name) {
        if (name == null || name.isBlank()) return Set.of();
        Object value = jwt.getClaims().get(name);
        if (value instanceof Collection<?> values) {
            Set<String> result = new LinkedHashSet<>();
            values.stream().map(String::valueOf).map(String::trim).filter(v -> !v.isBlank()).forEach(result::add);
            return result;
        }
        if (value instanceof String text) {
            return new LinkedHashSet<>(Arrays.stream(text.split("[ ,]+"))
                    .map(String::trim).filter(v -> !v.isBlank()).toList());
        }
        return Set.of();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IdentityResolutionException(message);
        return value;
    }
}
