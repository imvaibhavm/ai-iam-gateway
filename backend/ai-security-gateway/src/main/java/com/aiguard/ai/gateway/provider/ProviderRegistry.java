package com.aiguard.ai.gateway.provider;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ProviderRegistry {
    private final Map<String, ModelProvider> providers;
    public ProviderRegistry(List<ModelProvider> providers) {
        Map<String, ModelProvider> map = new LinkedHashMap<>();
        providers.forEach(p -> map.put(p.providerId().toLowerCase(), p));
        this.providers = Collections.unmodifiableMap(map);
    }
    public Optional<ModelProvider> find(String id) { return Optional.ofNullable(providers.get(id.toLowerCase())); }
    public Collection<ModelProvider> all() { return providers.values(); }
}
