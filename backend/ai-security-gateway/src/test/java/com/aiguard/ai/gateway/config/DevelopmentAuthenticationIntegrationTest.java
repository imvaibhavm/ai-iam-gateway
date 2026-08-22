package com.aiguard.ai.gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.aiguard.ai.gateway.iam.service.AppUserService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dev-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=integration-development-secret-at-least-32-bytes",
        "agent-runtime.shared-token=integration-agent-runtime-token"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class DevelopmentAuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserService users;

    @Test void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test void meAndAdminAuthorizationUseAuthoritativeLocalUser() throws Exception {
        String admin = token("admin@aiguard.com");
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.tenantId").value("default"));
        mvc.perform(get("/api/admin/providers").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        String intern = token("intern@aiguard.com");
        mvc.perform(get("/api/admin/providers").header("Authorization", "Bearer " + intern))
                .andExpect(status().isForbidden());
    }

    @Test void externalClaimsCannotElevateRoleAndDisabledUserFailsClosed() throws Exception {
        var external = jwt().jwt(token -> token.issuer("https://tenant.us.auth0.com/")
                .subject("auth0|intern-1").claim("email", "intern@aiguard.com")
                .claim("role", "ADMIN").claim("groups", java.util.List.of("security-admins"))
                .claim("https://aiguard.example/tenant_id", "default"));
        mvc.perform(get("/api/user/me").with(external)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INTERN"))
                .andExpect(jsonPath("$.groups[0]").value("security-admins"));
        mvc.perform(get("/api/admin/providers").with(external)).andExpect(status().isForbidden());

        users.updateEnabled("default", "intern@aiguard.com", false);
        mvc.perform(get("/api/user/me").with(external)).andExpect(status().isForbidden());
        users.updateEnabled("default", "intern@aiguard.com", true);
    }

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/dev-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"tenantId\":\"default\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("accessToken").asText();
    }
}
