package com.aiguard.ai.gateway.identity;

import org.springframework.security.oauth2.jwt.Jwt;

public interface ExternalIdentityClaimsMapper {
    ExternalIdentityClaims map(Jwt jwt);

    /** Maps non-authoritative attributes for an identity already bound by validated issuer and subject. */
    default ExternalIdentityClaims mapBound(Jwt jwt) { return map(jwt); }
}
