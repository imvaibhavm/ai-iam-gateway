package com.aiguard.ai.gateway.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IdentityResolverTest {
    private final IdentityResolver resolver = new IdentityResolver();

    @Test void resolvesAgentDelegationAndAbacClaims() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject("agent-7").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("email", "Agent@Example.com").claim("tenant_id", "Tenant-A")
                .claim("role", "ENGINEER").claim("actor_type", "AGENT")
                .claim("delegated_by", "user-2").claim("scp", List.of("llm.generate", "tool.read"))
                .claim("department", "engineering").claim("region", "eu").build();
        IdentityContext identity = resolver.require(new JwtAuthenticationToken(jwt));
        assertEquals(IdentityType.AGENT, identity.type());
        assertEquals("tenant-a", identity.tenantId());
        assertEquals("agent@example.com", identity.email());
        assertEquals("user-2", identity.delegatedBy());
        assertTrue(identity.scopes().contains("llm.generate"));
        assertEquals("engineering", identity.attributes().get("department"));
    }
}
