package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.repo.AppUserRepository;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserBulkImportTest {
    @Mock AppUserRepository repository;
    private AppUserService service;

    @BeforeEach void setUp() {
        service = new AppUserService(repository);
    }

    @Test void importsAuthoritativeAttributesWithoutAllowingTenantOverride() {
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findById("tenant-a|engineer@company.com")).thenReturn(Optional.empty());
        List<AppUser> imported = service.importUsers("tenant-a", List.of(new AppUserService.BulkUserDefinition(
                "Engineer@Company.com", UserRole.ENGINEER, true,
                Map.of("department", "engineering", "clearance", "INTERNAL", "tenantId", "tenant-b"),
                List.of("github.read", "mcp.github"))));

        assertThat(imported).singleElement().satisfies(user -> {
            assertThat(user.getTenantId()).isEqualTo("tenant-a");
            assertThat(user.getEmail()).isEqualTo("engineer@company.com");
            assertThat(user.getDepartment()).isEqualTo("engineering");
            assertThat(user.getPolicyAssignments()).isEqualTo("github.read,mcp.github");
        });
    }

    @Test void rejectsDuplicateEmailsAtomically() {
        var definition = new AppUserService.BulkUserDefinition("person@company.com", UserRole.INTERN,
                true, Map.of(), List.of());
        when(repository.findById("tenant-a|person@company.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.importUsers("tenant-a", List.of(definition, definition)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate email");
    }
}
