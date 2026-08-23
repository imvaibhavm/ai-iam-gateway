package com.aiguard.ai.gateway.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConfigurableOidcClaimsMapper implements ExternalIdentityClaimsMapper {
    private final String emailClaim;
    private final String groupsClaim;
    private final String emailVerifiedClaim;
    private final boolean requireVerifiedEmail;
    private final OidcUserInfoClient userInfoClient;
    private final Map<String, String> attributeClaims;

    public ConfigurableOidcClaimsMapper(
            @Value("${security.oidc.email-claim:email}") String emailClaim,
            @Value("${security.oidc.groups-claim:groups}") String groupsClaim,
            @Value("${security.oidc.tenant-claim:https://aiguard.example/tenant_id}") String tenantClaim,
            @Value("${security.oidc.department-claim:https://aiguard.example/department}") String departmentClaim,
            @Value("${security.oidc.clearance-claim:https://aiguard.example/clearance}") String clearanceClaim,
            @Value("${security.oidc.email-verified-claim:email_verified}") String emailVerifiedClaim,
            @Value("${security.oidc.require-verified-email:true}") boolean requireVerifiedEmail,
            OidcUserInfoClient userInfoClient) {
        this.emailClaim = emailClaim;
        this.groupsClaim = groupsClaim;
        this.emailVerifiedClaim = emailVerifiedClaim;
        this.requireVerifiedEmail = requireVerifiedEmail;
        this.userInfoClient = userInfoClient;
        this.attributeClaims = Map.of(
                "assertedTenant", tenantClaim,
                "department", departmentClaim,
                "clearance", clearanceClaim
        );
    }

    @Override
    public ExternalIdentityClaims map(Jwt jwt) {
        return map(jwt, true);
    }

    @Override
    public ExternalIdentityClaims mapBound(Jwt jwt) {
        return map(jwt, false);
    }

    private ExternalIdentityClaims map(Jwt jwt, boolean requireBootstrapEmail) {
        String subject = required(jwt.getSubject(), "OIDC subject claim is required");
        String email = stringClaim(jwt, emailClaim);
        Boolean emailVerified = booleanClaim(jwt, emailVerifiedClaim);
        if (requireBootstrapEmail && (email == null || (requireVerifiedEmail && emailVerified == null))) {
            OidcUserInfoClient.Profile profile = userInfoClient.fetch(jwt);
            if (email != null && profile.email() != null && !email.equalsIgnoreCase(profile.email())) {
                throw new IdentityResolutionException("OIDC email claim does not match UserInfo email");
            }
            if (email == null) email = profile.email();
            emailVerified = profile.emailVerified();
        }
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
        if (requireBootstrapEmail && (email == null || email.isBlank())) {
            throw new IdentityResolutionException("Verified OIDC email is required");
        }
        if (requireBootstrapEmail && requireVerifiedEmail && !Boolean.TRUE.equals(emailVerified)) {
            throw new IdentityResolutionException("Verified OIDC email is required");
        }
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

    private static Boolean booleanClaim(Jwt jwt, String name) {
        if (name == null || name.isBlank()) return null;
        Object value = jwt.getClaims().get(name);
        if (value == null) return null;
        return value instanceof Boolean flag ? flag : Boolean.valueOf(String.valueOf(value));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IdentityResolutionException(message);
        return value;
    }
}
