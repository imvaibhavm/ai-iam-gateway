package com.aiguard.ai.gateway.mcp;

import com.aiguard.ai.gateway.identity.IdentityContext;
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
@RequestMapping("/api/mcp/providers")
@RequiredArgsConstructor
public class UserMcpController {
    private final McpCatalogService catalog;
    private final IdentityResolver identities;

    @GetMapping
    public List<McpCatalogService.ProviderView> list(Authentication auth) {
        return catalog.userCatalog(identities.require(auth));
    }

    @PutMapping("/{providerId}/enabled/{enabled}")
    public McpCatalogService.ProviderView setEnabled(@PathVariable String providerId, @PathVariable boolean enabled,
                                                     Authentication auth) {
        IdentityContext identity = identities.require(auth);
        return catalog.setUserSelection(identity, providerId, enabled);
    }
}
