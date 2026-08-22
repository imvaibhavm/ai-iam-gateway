package com.aiguard.ai.gateway.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ConfigurableOidcClaimsMapperTest {
    @Test void mapsAlternateProviderClaimNamesWithoutProviderSpecificCode() {
        var mapper = new ConfigurableOidcClaimsMapper("preferred_username", "roles", "org", "dept", "level");
        Jwt jwt = Jwt.withTokenValue("validated").header("alg", "RS256").issuer("https://keycloak.example/realms/acme")
                .subject("subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "User@Example.com").claim("roles", List.of("developers"))
                .claim("org", "tenant-a").claim("dept", "engineering").claim("level", "HIGH").build();

        ExternalIdentityClaims claims = mapper.map(jwt);

        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.groups()).containsExactly("developers");
        assertThat(claims.attributes()).containsEntry("assertedTenant", "tenant-a")
                .containsEntry("department", "engineering").containsEntry("clearance", "HIGH");
    }
}
