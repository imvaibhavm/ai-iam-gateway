package com.aiguard.ai.gateway.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${security.oidc.enabled:false}") boolean oidcEnabled,
            @Value("${security.oidc.issuer-uri:}") String issuer,
            @Value("${security.oidc.audience:}") String audience,
            @Value("${security.jwt.dev-token-enabled:false}") boolean devEnabled,
            @Value("${security.jwt.secret:}") String devSecret,
            @Value("${security.jwt.issuer:ai-security-gateway}") String devIssuer) {
        if (oidcEnabled && devEnabled) {
            throw new IllegalStateException("OIDC and development JWT modes are mutually exclusive");
        }
        if (oidcEnabled) {
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalStateException("OIDC_ISSUER_URI is required when OIDC is enabled");
            }
            NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
            OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
            if (audience != null && !audience.isBlank()) {
                validator = new DelegatingOAuth2TokenValidator<>(validator, new AudienceValidator(audience));
            }
            decoder.setJwtValidator(validator);
            return decoder;
        }
        if (devEnabled) {
            SecretKey key = secretKey(devSecret);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(devIssuer));
            return decoder;
        }
        return token -> { throw new JwtException("No JWT authentication mode is enabled"); };
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${security.jwt.secret:}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey(secret)));
    }

    private static SecretKey secretKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String audience;
        AudienceValidator(String audience) { this.audience = audience; }
        @Override public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience().contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "Required audience is missing", null));
        }
    }
}
