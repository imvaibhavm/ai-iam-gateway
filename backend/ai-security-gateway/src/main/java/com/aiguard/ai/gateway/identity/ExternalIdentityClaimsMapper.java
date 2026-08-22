package com.aiguard.ai.gateway.identity;

import org.springframework.security.oauth2.jwt.Jwt;

public interface ExternalIdentityClaimsMapper {
    ExternalIdentityClaims map(Jwt jwt);
}
