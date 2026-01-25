package com.aiguard.ai.gateway.llm;

import com.aiguard.ai.gateway.huggingface.HuggingFaceClient;
import com.aiguard.ai.gateway.ollama.OllamaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class LlmRouter implements LlmClient {

    private final OllamaClient ollamaClient;
    private final HuggingFaceClient huggingFaceClient;

    @Value("${llm.provider:ollama}")
    private String provider;

    @Override
    public String generate(String userMessage) {
        return selected().generate(userMessage);
    }

    @Override
    public void stream(String userMessage, Consumer<String> onToken) {
        selected().stream(userMessage, onToken);
    }

    private LlmClient selected() {
        if ("huggingface".equalsIgnoreCase(provider)) return huggingFaceClient;
        return ollamaClient;
    }
}
