package com.aiguard.ai.gateway.identity;

import com.aiguard.ai.gateway.iam.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class DevTokenController {
    private final JwtEncoder encoder;
    private final boolean enabled;
    private final String issuer;

    public DevTokenController(JwtEncoder encoder,
                              @Value("${security.jwt.dev-token-enabled:false}") boolean enabled,
                              @Value("${security.jwt.issuer}") String issuer) {
        this.encoder = encoder;
        this.enabled = enabled;
        this.issuer = issuer;
    }

    @PostMapping("/dev-token")
    public Map<String, String> token(@RequestBody DevTokenRequest request) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email required");
        }
        Instant now = Instant.now();
        String email = request.email().trim().toLowerCase();
        String tenant = request.tenantId() == null || request.tenantId().isBlank()
                ? "default" : request.tenantId().trim().toLowerCase();
        UserRole role = request.role() == null ? UserRole.INTERN : request.role();
        IdentityType actorType = request.actorType() == null ? IdentityType.HUMAN : request.actorType();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).issuedAt(now).expiresAt(now.plus(8, ChronoUnit.HOURS))
                .subject(email).claim("email", email).claim("tenant_id", tenant)
                .claim("role", role.name()).claim("actor_type", actorType.name());
        if (request.delegatedBy() != null && !request.delegatedBy().isBlank()) {
            claims.claim("delegated_by", request.delegatedBy());
        }
        if (request.scopes() != null && !request.scopes().isEmpty()) {
            claims.claim("scp", request.scopes());
        }
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        return Map.of("accessToken", token, "tokenType", "Bearer");
    }

    public record DevTokenRequest(String email, String tenantId, UserRole role, IdentityType actorType,
                                  String delegatedBy, java.util.Set<String> scopes) { }
}
