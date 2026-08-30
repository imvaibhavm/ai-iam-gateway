package com.aiguard.ai.gateway.audit;

import com.aiguard.ai.gateway.audit.controller.AuditMetricsController;
import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.repo.AuditLogRepository;
import com.aiguard.ai.gateway.identity.IdentityContext;
import com.aiguard.ai.gateway.identity.IdentityResolver;
import com.aiguard.ai.gateway.iam.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditMetricsControllerTest {
    @Mock AuditLogRepository repository;
    @Mock IdentityResolver identities;
    @Mock Authentication authentication;

    @Test void aggregatesDecisionsAndPiiForAuthoritativeTenant() {
        when(identities.require(authentication)).thenReturn(new IdentityContext("sub", "admin@company.com", "tenant-a", UserRole.ADMIN));
        AuditLog allowed = AuditLog.builder().ts(Instant.now()).allowed(true).piiTypes("EMAIL,PHONE").build();
        AuditLog denied = AuditLog.builder().ts(Instant.now()).allowed(false).piiTypes("EMAIL").outputRedacted(true).build();
        when(repository.findByTenantIdAndTsAfterOrderByTsAsc(eq("tenant-a"), any(), any())).thenReturn(List.of(allowed, denied));

        var result = new AuditMetricsController(repository, identities).metrics("24h", authentication);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.allowed()).isEqualTo(1);
        assertThat(result.denied()).isEqualTo(1);
        assertThat(result.piiHandled()).isEqualTo(2);
        assertThat(result.piiByType()).containsEntry("EMAIL", 2L).containsEntry("PHONE", 1L);
    }
}
