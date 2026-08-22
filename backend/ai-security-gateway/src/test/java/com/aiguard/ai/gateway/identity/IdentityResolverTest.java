package com.aiguard.ai.gateway.identity;

import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityResolverTest {
    private final ConfigurableOidcClaimsMapper mapper = new ConfigurableOidcClaimsMapper(
            "email", "groups", "tenant", "department", "clearance");
    private final AppUserService users = mock(AppUserService.class);

    @Test void resolvesExternalClaimsButUsesAuthoritativeDatabaseRoleAndTenant() {
        AppUser local = user("tenant-a", "person@example.com", UserRole.INTERN, true);
        when(users.resolveExternal(any())).thenReturn(local);
        IdentityResolver resolver = new IdentityResolver(mapper, users, false, "dev");
        Jwt jwt = jwt("https://issuer.example/", "oidc|7")
                .claim("email", "Person@Example.com").claim("tenant", "tenant-a")
                .claim("role", "ADMIN").claim("groups", List.of("engineering"))
                .claim("department", "product").build();

        IdentityContext identity = resolver.require(new JwtAuthenticationToken(jwt));

        assertThat(identity.role()).isEqualTo(UserRole.INTERN);
        assertThat(identity.tenantId()).isEqualTo("tenant-a");
        assertThat(identity.groups()).containsExactly("engineering");
        assertThat(identity.attributes()).containsEntry("department", "product").doesNotContainKey("assertedTenant");
    }

    @Test void developmentModePreservesDelegationAndUsesLocalUserState() {
        AppUser local = user("tenant-a", "agent@example.com", UserRole.ENGINEER, true);
        when(users.getOrCreate("tenant-a", "agent@example.com", UserRole.ADMIN)).thenReturn(local);
        IdentityResolver resolver = new IdentityResolver(mapper, users, true, "dev-issuer");
        Jwt jwt = jwt("dev-issuer", "agent@example.com")
                .claim("email", "Agent@Example.com").claim("tenant_id", "Tenant-A")
                .claim("role", "ADMIN").claim("actor_type", "AGENT")
                .claim("delegated_by", "user-2").claim("scp", List.of("tool.read")).build();

        IdentityContext identity = resolver.require(new JwtAuthenticationToken(jwt));

        assertThat(identity.role()).isEqualTo(UserRole.ENGINEER);
        assertThat(identity.type()).isEqualTo(IdentityType.AGENT);
        assertThat(identity.delegatedBy()).isEqualTo("user-2");
    }

    @Test void rejectsNonJwtPrincipal() {
        IdentityResolver resolver = new IdentityResolver(mapper, users, false, "dev");
        assertThatThrownBy(() -> resolver.require(null)).isInstanceOf(IllegalStateException.class);
    }

    private static Jwt.Builder jwt(String issuer, String subject) {
        return Jwt.withTokenValue("validated-token").header("alg", "RS256").issuer(issuer).subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    private static AppUser user(String tenant, String email, UserRole role, boolean enabled) {
        return AppUser.builder().id(tenant + "|" + email).tenantId(tenant).email(email).role(role).enabled(enabled).build();
    }
}
