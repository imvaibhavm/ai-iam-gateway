package com.aiguard.ai.gateway.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurableOidcClaimsMapperTest {
    @Test void mapsAlternateProviderClaimNamesWithoutProviderSpecificCode() {
        var mapper = new ConfigurableOidcClaimsMapper("preferred_username", "roles", "org", "dept", "level",
                "verified", true, mock(OidcUserInfoClient.class));
        Jwt jwt = Jwt.withTokenValue("validated").header("alg", "RS256").issuer("https://keycloak.example/realms/acme")
                .subject("subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "User@Example.com").claim("roles", List.of("developers"))
                .claim("verified", true).claim("org", "tenant-a").claim("dept", "engineering")
                .claim("level", "HIGH").build();

        ExternalIdentityClaims claims = mapper.map(jwt);

        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.groups()).containsExactly("developers");
        assertThat(claims.attributes()).containsEntry("assertedTenant", "tenant-a")
                .containsEntry("department", "engineering").containsEntry("clearance", "HIGH");
    }

    @Test void obtainsVerifiedEmailFromProviderNeutralUserInfoWhenAccessTokenOmitsIt() {
        OidcUserInfoClient userInfo = mock(OidcUserInfoClient.class);
        var mapper = new ConfigurableOidcClaimsMapper("email", "groups", "tenant", "department", "clearance",
                "email_verified", true, userInfo);
        Jwt jwt = Jwt.withTokenValue("validated").header("alg", "RS256").issuer("https://issuer.example/")
                .subject("subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(userInfo.fetch(jwt)).thenReturn(new OidcUserInfoClient.Profile("Admin@Example.com", true));

        assertThat(mapper.map(jwt).email()).isEqualTo("admin@example.com");
    }

    @Test void unverifiedEmailFailsClosed() {
        OidcUserInfoClient userInfo = mock(OidcUserInfoClient.class);
        var mapper = new ConfigurableOidcClaimsMapper("email", "groups", "tenant", "department", "clearance",
                "email_verified", true, userInfo);
        Jwt jwt = Jwt.withTokenValue("validated").header("alg", "RS256").issuer("https://issuer.example/")
                .subject("subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("email", "admin@example.com").claim("email_verified", false).build();

        assertThatThrownBy(() -> mapper.map(jwt)).isInstanceOf(IdentityResolutionException.class)
                .hasMessageContaining("Verified");
        verifyNoInteractions(userInfo);
    }
}
