package com.aiguard.ai.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AgentRuntimeAuthenticationFilter agentFilter) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();

                    // ✅ allow your Vercel domain
                    config.setAllowedOrigins(List.of(
                            "http://localhost:3000",
                            "https://ai-iam-gateway.vercel.app"
                    ));

                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);

                    return config;
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/actuator/health", "/api/auth/dev-token").permitAll()
                        .requestMatchers("/internal/agent/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(agentFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }
}
