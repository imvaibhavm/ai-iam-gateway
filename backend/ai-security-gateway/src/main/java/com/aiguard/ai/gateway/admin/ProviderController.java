package com.aiguard.ai.gateway.admin;

import com.aiguard.ai.gateway.provider.ProviderRegistry;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/providers")
public class ProviderController {
    private final ProviderRegistry registry;
    public ProviderController(ProviderRegistry registry) { this.registry = registry; }
    @GetMapping
    public List<ProviderStatus> list() {
        return registry.all().stream().map(p -> new ProviderStatus(p.providerId(), p.modelId(), p.cloud(), p.health().available(), p.health().reason())).toList();
    }
    public record ProviderStatus(String provider, String model, boolean cloud, boolean available, String reason) { }
}
