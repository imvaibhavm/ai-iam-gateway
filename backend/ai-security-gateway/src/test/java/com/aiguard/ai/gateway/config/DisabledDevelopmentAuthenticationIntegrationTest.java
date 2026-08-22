package com.aiguard.ai.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:disabled-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.oidc.enabled=false", "security.jwt.dev-token-enabled=false",
        "security.jwt.secret=integration-development-secret-at-least-32-bytes",
        "agent-runtime.shared-token=integration-agent-runtime-token"
})
@AutoConfigureMockMvc
class DisabledDevelopmentAuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void developmentTokenEndpointIsDisabled() throws Exception {
        mvc.perform(post("/api/auth/dev-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aiguard.com\"}"))
                .andExpect(status().isUnauthorized());
    }
}
