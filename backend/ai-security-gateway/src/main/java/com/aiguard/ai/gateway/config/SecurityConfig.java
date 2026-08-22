package com.aiguard.ai.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Configuration
public class SecurityConfig {
    private static final Logger AUTH_LOG = LoggerFactory.getLogger("SECURITY_AUTHENTICATION");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AgentRuntimeAuthenticationFilter agentFilter,
            AuthenticationAuditFilter authenticationAuditFilter,
            @Value("${security.cors.allowed-origins:http://localhost:3000}") List<String> allowedOrigins,
            @Value("${security.jwt.dev-token-enabled:false}") boolean devTokenEnabled) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();

                    config.setAllowedOrigins(allowedOrigins);

                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);

                    return config;
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/actuator/health").permitAll()
                        .requestMatchers("/api/auth/dev-token").access((authentication, context) ->
                                new org.springframework.security.authorization.AuthorizationDecision(devTokenEnabled))
                        .requestMatchers("/internal/agent/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(agentFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authenticationAuditFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint((request, response, exception) -> {
                            AUTH_LOG.warn("event=authentication_failure requestId={} category=invalid_or_missing_access_token path={}",
                                    AuthenticationAuditFilter.requestId(request), request.getRequestURI());
                            response.sendError(401, "invalid_or_missing_access_token");
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(403, "forbidden"))
                        .jwt(jwt -> {}))
                .build();
    }
}
