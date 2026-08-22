package com.aiguard.ai.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Content-free authentication metadata. Tokens and full claim payloads are never logged. */
@Component
public class AuthenticationAuditFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger("SECURITY_AUTHENTICATION");

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt) {
            String requestId = requestId(request);
            String issuer = jwt.getClaimAsString("iss");
            LOG.info("event=authentication_success requestId={} subject={} issuer={} identityType=HUMAN",
                    safe(requestId), safe(jwt.getSubject()), safe(issuer));
            response.setHeader("X-Request-ID", requestId);
        }
        chain.doFilter(request, response);
    }

    static String requestId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Request-ID");
        return supplied == null || !supplied.matches("[A-Za-z0-9._-]{8,128}") ? UUID.randomUUID().toString() : supplied;
    }
    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\r\\n\\t]", "_");
    }
}
