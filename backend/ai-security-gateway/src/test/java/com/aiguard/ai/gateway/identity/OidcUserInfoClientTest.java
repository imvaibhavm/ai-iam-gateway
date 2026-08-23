package com.aiguard.ai.gateway.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OidcUserInfoClientTest {
    @Test void retrievesVerifiedProfileAndChecksSubject() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OidcUserInfoClient client = new OidcUserInfoClient(builder, "https://issuer.example/", "");
        server.expect(requestTo("https://issuer.example/userinfo"))
                .andExpect(header("Authorization", "Bearer validated-token"))
                .andRespond(withSuccess("{\"sub\":\"subject-1\",\"email\":\"admin@example.com\",\"email_verified\":true}",
                        MediaType.APPLICATION_JSON));

        OidcUserInfoClient.Profile profile = client.fetch(jwt("subject-1"));

        assertThat(profile.email()).isEqualTo("admin@example.com");
        assertThat(profile.emailVerified()).isTrue();
        server.verify();
    }

    @Test void mismatchedSubjectFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OidcUserInfoClient client = new OidcUserInfoClient(builder, "https://issuer.example/", "");
        server.expect(requestTo("https://issuer.example/userinfo"))
                .andRespond(withSuccess("{\"sub\":\"other-subject\",\"email\":\"admin@example.com\",\"email_verified\":true}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch(jwt("subject-1")))
                .isInstanceOf(IdentityResolutionException.class).hasMessageContaining("subject");
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("validated-token").header("alg", "RS256").issuer("https://issuer.example/")
                .subject(subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
