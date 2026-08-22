package com.aiguard.ai.gateway.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AgentRuntimeAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] expected;
    public AgentRuntimeAuthenticationFilter(@Value("${agent-runtime.shared-token}") String token) {
        expected = token.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/agent/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Agent-Runtime-Token");
        if (supplied == null || expected.length < 24 || !MessageDigest.isEqual(expected,
                supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "agent_runtime_authentication_failed");
            return;
        }
        chain.doFilter(request, response);
    }
}
