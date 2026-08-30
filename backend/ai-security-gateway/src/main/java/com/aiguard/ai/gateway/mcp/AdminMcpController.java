package com.aiguard.ai.gateway.mcp;

import com.aiguard.ai.gateway.identity.IdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mcp/providers")
@RequiredArgsConstructor
public class AdminMcpController {
    private final McpCatalogService catalog;
    private final IdentityResolver identities;

    @GetMapping
    public List<McpCatalogService.ProviderView> list(Authentication auth) {
        return catalog.adminCatalog(identities.require(auth).tenantId());
    }

    @PutMapping("/{providerId}/enabled/{enabled}")
    public McpCatalogService.ProviderView setEnabled(@PathVariable String providerId, @PathVariable boolean enabled,
                                                     Authentication auth) {
        return catalog.setTenantAvailability(identities.require(auth).tenantId(), providerId, enabled);
    }
}
