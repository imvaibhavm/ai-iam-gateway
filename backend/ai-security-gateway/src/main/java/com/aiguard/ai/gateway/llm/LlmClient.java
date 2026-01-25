package com.aiguard.ai.gateway.llm;

import java.util.function.Consumer;

public interface LlmClient {
    String generate(String userMessage);
    void stream(String userMessage, Consumer<String> onToken);
}
