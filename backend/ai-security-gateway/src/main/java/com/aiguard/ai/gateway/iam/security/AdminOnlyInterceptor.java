package com.aiguard.ai.gateway.iam.security;

import com.aiguard.ai.gateway.common.ApiErrorResponse;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AdminOnlyInterceptor implements HandlerInterceptor {

    private final AppUserService userService;
    private final ObjectMapper objectMapper; // ✅ Spring injected

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        String email = request.getHeader("X-User-Email");

        if (email == null || email.isBlank()) {
            writeError(response, request, 401, "UNAUTHORIZED", "Missing X-User-Email header");
            return false;
        }

        AppUser user = userService.getOrCreateDefault(email);

        if (!user.isEnabled()) {
            writeError(response, request, 403, "FORBIDDEN", "User disabled");
            return false;
        }

        if (user.getRole() != UserRole.ADMIN) {
            writeError(response, request, 403, "FORBIDDEN", "Admin access required");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            int status,
                            String error,
                            String message) {

        try {
            ApiErrorResponse body = new ApiErrorResponse(
                    Instant.now(),
                    status,
                    error,
                    message,
                    request.getRequestURI()
            );

            response.resetBuffer(); // ✅ clears partial committed data
            response.setStatus(status);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            response.flushBuffer();

        } catch (Exception ignored) {
            // last resort fallback (never throw from interceptor)
            try {
                response.setStatus(status);
            } catch (Exception ignored2) {}
        }
    }
}
