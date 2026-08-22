package com.aiguard.ai.gateway.routing;

import com.aiguard.ai.gateway.provider.ModelProvider;
import java.util.List;

public record RoutingDecision(ModelProvider selected, String reason, List<ModelProvider> fallbacks) { }
