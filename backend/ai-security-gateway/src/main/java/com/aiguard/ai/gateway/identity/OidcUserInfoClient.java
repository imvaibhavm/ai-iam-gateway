package com.aiguard.ai.gateway.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

/**
 * Provider-neutral OIDC UserInfo client. The access token has already been
 * cryptographically validated by Spring Security before this component runs.
 */
@Component
public class OidcUserInfoClient {
    private final RestClient restClient;
    private final URI endpoint;

    public OidcUserInfoClient(RestClient.Builder builder,
                              @Value("${security.oidc.issuer-uri:}") String issuer,
                              @Value("${security.oidc.userinfo-uri:}") String configuredEndpoint) {
        this.restClient = builder.build();
        this.endpoint = resolveEndpoint(issuer, configuredEndpoint);
    }

    Profile fetch(Jwt jwt) {
        if (endpoint == null) {
            throw new IdentityResolutionException("OIDC UserInfo endpoint is required when identity claims are absent");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(endpoint)
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                    .retrieve()
                    .body(Map.class);
            if (response == null) throw new IdentityResolutionException("OIDC UserInfo response is empty");
            String subject = string(response.get("sub"));
            if (subject == null || !subject.equals(jwt.getSubject())) {
                throw new IdentityResolutionException("OIDC UserInfo subject does not match validated access token");
            }
            return new Profile(string(response.get("email")), booleanValue(response.get("email_verified")));
        } catch (IdentityResolutionException exception) {
            throw exception;
        } catch (Exception exception) {
            // Never include the response body or bearer token in this error.
            throw new IdentityResolutionException("OIDC UserInfo lookup failed");
        }
    }

    private static URI resolveEndpoint(String issuer, String configuredEndpoint) {
        String value = configuredEndpoint == null ? "" : configuredEndpoint.trim();
        if (!value.isBlank()) return URI.create(value);
        String normalizedIssuer = issuer == null ? "" : issuer.trim();
        if (normalizedIssuer.isBlank()) return null;
        return URI.create(normalizedIssuer.endsWith("/")
                ? normalizedIssuer + "userinfo" : normalizedIssuer + "/userinfo");
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(string(value));
    }

    record Profile(String email, boolean emailVerified) { }
}
