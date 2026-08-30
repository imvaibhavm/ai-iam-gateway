package com.aiguard.ai.gateway.mcp;

import com.aiguard.ai.gateway.identity.IdentityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class McpCatalogService {
    private final McpProviderSettingRepository providerSettings;
    private final UserMcpSettingRepository userSettings;

    private static final List<CatalogItem> CATALOG = List.of(
            new CatalogItem("github", "GitHub", "Code", "Search repositories, read files and propose protected code changes.", "github.read"),
            new CatalogItem("slack", "Slack", "Collaboration", "Search approved conversations and prepare messages.", "slack.read"),
            new CatalogItem("notion", "Notion", "Knowledge", "Retrieve approved workspace pages and knowledge.", "notion.read"),
            new CatalogItem("postgres", "PostgreSQL", "Data", "Run policy-constrained read queries against registered databases.", "database.read"),
            new CatalogItem("google-drive", "Google Drive", "Knowledge", "Retrieve approved files from managed drives.", "drive.read"),
            new CatalogItem("jira", "Jira", "Work management", "Read issues and prepare controlled workflow updates.", "jira.read"),
            new CatalogItem("sentry", "Sentry", "Operations", "Inspect approved application errors and incident context.", "sentry.read")
    );

    @Transactional(readOnly = true)
    public List<ProviderView> adminCatalog(String tenantId) {
        Map<String, McpProviderSetting> settings = byProvider(providerSettings.findByTenantId(tenantId));
        return CATALOG.stream().map(item -> view(item, settings.get(item.id()), null)).toList();
    }

    @Transactional
    public ProviderView setTenantAvailability(String tenantId, String providerId, boolean enabled) {
        CatalogItem item = requireCatalog(providerId);
        String id = tenantId + "|" + providerId;
        McpProviderSetting setting = providerSettings.findById(id).orElseGet(() -> McpProviderSetting.builder()
                .id(id).tenantId(tenantId).providerId(providerId).build());
        setting.setAdminEnabled(enabled);
        providerSettings.save(setting);
        return view(item, setting, null);
    }

    @Transactional(readOnly = true)
    public List<ProviderView> userCatalog(IdentityContext identity) {
        Map<String, McpProviderSetting> tenant = byProvider(providerSettings.findByTenantId(identity.tenantId()));
        Map<String, UserMcpSetting> user = new LinkedHashMap<>();
        userSettings.findByTenantIdAndUserEmail(identity.tenantId(), identity.email())
                .forEach(setting -> user.put(setting.getProviderId(), setting));
        return CATALOG.stream().map(item -> view(item, tenant.get(item.id()), user.get(item.id()))).toList();
    }

    @Transactional
    public ProviderView setUserSelection(IdentityContext identity, String providerId, boolean enabled) {
        CatalogItem item = requireCatalog(providerId);
        McpProviderSetting tenantSetting = providerSettings.findById(identity.tenantId() + "|" + providerId).orElse(null);
        if (enabled && (tenantSetting == null || !tenantSetting.isAdminEnabled())) {
            throw new IllegalArgumentException("This connection is not enabled by the workspace administrator");
        }
        String id = identity.tenantId() + "|" + identity.email() + "|" + providerId;
        UserMcpSetting setting = userSettings.findById(id).orElseGet(() -> UserMcpSetting.builder()
                .id(id).tenantId(identity.tenantId()).userEmail(identity.email()).providerId(providerId).build());
        setting.setEnabled(enabled);
        userSettings.save(setting);
        return view(item, tenantSetting, setting);
    }

    @Transactional(readOnly = true)
    public void requireUserEnabled(IdentityContext identity, List<String> requestedProviders) {
        if (requestedProviders == null || requestedProviders.isEmpty()) return;
        Map<String, ProviderView> available = new LinkedHashMap<>();
        userCatalog(identity).forEach(provider -> available.put(provider.id(), provider));
        for (String requested : requestedProviders) {
            ProviderView provider = available.get(requested);
            if (provider == null || !provider.tenantEnabled() || !provider.userEnabled()) {
                throw new IllegalArgumentException("Requested tool connection is not enabled: " + requested);
            }
        }
    }

    private Map<String, McpProviderSetting> byProvider(List<McpProviderSetting> settings) {
        Map<String, McpProviderSetting> result = new LinkedHashMap<>();
        settings.forEach(setting -> result.put(setting.getProviderId(), setting));
        return result;
    }

    private CatalogItem requireCatalog(String providerId) {
        return CATALOG.stream().filter(item -> item.id().equals(providerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP provider"));
    }

    private ProviderView view(CatalogItem item, McpProviderSetting tenant, UserMcpSetting user) {
        boolean tenantEnabled = tenant != null && tenant.isAdminEnabled();
        return new ProviderView(item.id(), item.name(), item.category(), item.description(), item.requiredScope(),
                tenantEnabled, tenantEnabled && user != null && user.isEnabled(), "CATALOG");
    }

    private record CatalogItem(String id, String name, String category, String description, String requiredScope) { }
    public record ProviderView(String id, String name, String category, String description, String requiredScope,
                               boolean tenantEnabled, boolean userEnabled, String connectionStatus) { }
}
