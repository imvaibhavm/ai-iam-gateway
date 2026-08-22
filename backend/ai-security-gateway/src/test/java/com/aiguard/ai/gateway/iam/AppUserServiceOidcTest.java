package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.repo.AppUserRepository;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import com.aiguard.ai.gateway.identity.ExternalIdentityClaims;
import com.aiguard.ai.gateway.identity.IdentityResolutionException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppUserServiceOidcTest {
    private final AppUserRepository repo = mock(AppUserRepository.class);
    private final AppUserService service = new AppUserService(repo);

    @Test void bindsValidatedExternalIdentityToExactlyOneExistingUser() {
        AppUser user = user("tenant-a", UserRole.ENGINEER, true);
        when(repo.findByExternalIssuerAndExternalSubject("https://issuer/", "sub-1")).thenReturn(Optional.empty());
        when(repo.findByEmailIgnoreCase("user@example.com")).thenReturn(List.of(user));
        when(repo.save(user)).thenReturn(user);
        assertThat(service.resolveExternal(claims("tenant-a"))).isSameAs(user);
        assertThat(user.getExternalSubject()).isEqualTo("sub-1");
    }

    @Test void disabledUserIsDeniedEvenWithValidExternalIdentity() {
        AppUser user = user("tenant-a", UserRole.ADMIN, false);
        when(repo.findByExternalIssuerAndExternalSubject(anyString(), anyString())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.resolveExternal(claims("tenant-a")))
                .isInstanceOf(IdentityResolutionException.class).hasMessageContaining("disabled");
    }

    @Test void tenantMismatchAndAmbiguousEmailFailClosed() {
        AppUser user = user("tenant-a", UserRole.ADMIN, true);
        when(repo.findByExternalIssuerAndExternalSubject(anyString(), anyString())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.resolveExternal(claims("tenant-b"))).hasMessageContaining("tenant");
        reset(repo);
        when(repo.findByExternalIssuerAndExternalSubject(anyString(), anyString())).thenReturn(Optional.empty());
        when(repo.findByEmailIgnoreCase(anyString())).thenReturn(List.of(user, user("tenant-b", UserRole.INTERN, true)));
        assertThatThrownBy(() -> service.resolveExternal(claims("tenant-a"))).hasMessageContaining("multiple");
    }

    @Test void administrativeUpsertPreservesExternalIdentityBinding() {
        AppUser user = user("tenant-a", UserRole.INTERN, true);
        user.setExternalIssuer("https://issuer/"); user.setExternalSubject("sub-1");
        when(repo.findById(user.getId())).thenReturn(Optional.of(user));
        when(repo.save(user)).thenReturn(user);
        AppUser updated = service.upsertUser("tenant-a", "user@example.com", UserRole.ADMIN, true);
        assertThat(updated.getExternalIssuer()).isEqualTo("https://issuer/");
        assertThat(updated.getExternalSubject()).isEqualTo("sub-1");
    }

    private static ExternalIdentityClaims claims(String tenant) {
        return new ExternalIdentityClaims("https://issuer/", "sub-1", "user@example.com", Set.of(),
                Map.of("assertedTenant", tenant));
    }
    private static AppUser user(String tenant, UserRole role, boolean enabled) {
        return AppUser.builder().id(tenant + "|user@example.com").tenantId(tenant).email("user@example.com")
                .role(role).enabled(enabled).build();
    }
}
