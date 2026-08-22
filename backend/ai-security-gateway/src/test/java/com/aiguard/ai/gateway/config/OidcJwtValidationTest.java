package com.aiguard.ai.gateway.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OidcJwtValidationTest {
    private static final String ISSUER = "https://tenant.example/";
    private static final String AUDIENCE = "https://api.aiguard.example";
    private RSAKey signingKey;
    private JwtEncoder encoder;
    private NimbusJwtDecoder decoder;

    @BeforeEach void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)));
        decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), new JwtConfig.AudienceValidator(AUDIENCE)));
    }

    @Test void acceptsValidAuth0StyleAccessToken() {
        assertThat(decoder.decode(token(signingKey, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60))).getSubject())
                .isEqualTo("auth0|123");
    }

    @Test void rejectsInvalidSignature() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID("test-key").generate();
        assertThatThrownBy(() -> decoder.decode(token(attacker, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtException.class);
    }

    @Test void rejectsExpiredWrongIssuerAndWrongAudience() {
        assertThatThrownBy(() -> decoder.decode(token(signingKey, ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(60))))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(token(signingKey, "https://other.example/", List.of(AUDIENCE), Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(token(signingKey, ISSUER, List.of("wrong-audience"), Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtValidationException.class);
    }

    private String token(RSAKey key, String issuer, List<String> audience, Instant expiry) {
        JwtEncoder signer = key == signingKey ? encoder
                : new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("auth0|123")
                .audience(audience).issuedAt(expiry.minusSeconds(60)).expiresAt(expiry)
                .claim("email", "admin@aiguard.com").build();
        return signer.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(), claims)).getTokenValue();
    }
}
