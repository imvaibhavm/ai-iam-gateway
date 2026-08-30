package com.aiguard.ai.gateway.mcp;

import com.aiguard.ai.gateway.identity.IdentityContext;
import com.aiguard.ai.gateway.iam.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpCatalogServiceTest {
    @Mock McpProviderSettingRepository providerSettings;
    @Mock UserMcpSettingRepository userSettings;
    private McpCatalogService service;
    private final IdentityContext identity = new IdentityContext("subject", "user@company.com", "tenant-a", UserRole.ENGINEER);

    @BeforeEach void setUp() { service = new McpCatalogService(providerSettings, userSettings); }

    @Test void userCannotEnableProviderThatAdminDidNotApprove() {
        when(providerSettings.findById("tenant-a|github")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setUserSelection(identity, "github", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("workspace administrator");
    }

    @Test void approvedProviderCanBeEnabledPerUser() {
        var tenant = McpProviderSetting.builder().id("tenant-a|github").tenantId("tenant-a")
                .providerId("github").adminEnabled(true).build();
        when(providerSettings.findById("tenant-a|github")).thenReturn(Optional.of(tenant));
        when(userSettings.findById("tenant-a|user@company.com|github")).thenReturn(Optional.empty());
        when(userSettings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.setUserSelection(identity, "github", true).userEnabled()).isTrue();
    }

    @Test void catalogContainsCommonGovernedConnections() {
        when(providerSettings.findByTenantId("tenant-a")).thenReturn(List.of());
        assertThat(service.adminCatalog("tenant-a")).extracting(McpCatalogService.ProviderView::id)
                .contains("github", "slack", "notion", "postgres", "google-drive", "jira", "sentry");
    }

    @Test void agentCannotRequestAConnectionTheUserDidNotEnable() {
        when(providerSettings.findByTenantId("tenant-a")).thenReturn(List.of(McpProviderSetting.builder()
                .id("tenant-a|github").tenantId("tenant-a").providerId("github").adminEnabled(true).build()));
        when(userSettings.findByTenantIdAndUserEmail("tenant-a", "user@company.com")).thenReturn(List.of());
        assertThatThrownBy(() -> service.requireUserEnabled(identity, List.of("github")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not enabled");
    }
}
