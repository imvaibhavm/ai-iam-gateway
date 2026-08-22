package com.aiguard.ai.gateway.provider;

import java.util.function.Consumer;

public interface ModelProvider {
    String providerId();
    String modelId();
    boolean cloud();
    ProviderHealth health();
    ModelResponse generate(ModelRequest request);
    ModelResponse stream(ModelRequest request, Consumer<String> onToken);
}
