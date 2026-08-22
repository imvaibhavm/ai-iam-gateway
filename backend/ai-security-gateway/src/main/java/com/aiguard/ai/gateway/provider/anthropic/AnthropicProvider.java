package com.aiguard.ai.gateway.provider.anthropic;

import com.aiguard.ai.gateway.provider.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.function.Consumer;

@Component
public class AnthropicProvider implements ModelProvider {
    private final String token, model, baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicProvider(@Value("${anthropic.token:}") String token, @Value("${anthropic.model}") String model,
            @Value("${anthropic.baseUrl}") String baseUrl,
            @Value("${gateway.provider-timeout-seconds:45}") int timeout) {
        this.token = token; this.model = model; this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
    }
    public String providerId() { return "anthropic"; }
    public String modelId() { return model; }
    public boolean cloud() { return true; }
    public ProviderHealth health() { return token == null || token.isBlank() ? ProviderHealth.down("Anthropic token missing") : ProviderHealth.up(); }
    public ModelResponse generate(ModelRequest request) {
        long start = System.nanoTime();
        try {
            String body = "{\"model\":" + mapper.writeValueAsString(model) + ",\"max_tokens\":" + request.maxTokens()
                    + ",\"messages\":[{\"role\":\"user\",\"content\":" + mapper.writeValueAsString(request.prompt()) + "}]}";
            HttpRequest http = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/messages"))
                    .timeout(Duration.ofSeconds(45)).header("x-api-key", token)
                    .header("anthropic-version", "2023-06-01").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(http, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("Anthropic HTTP " + response.statusCode());
            JsonNode json = mapper.readTree(response.body());
            return new ModelResponse(json.at("/content/0/text").asText(), providerId(), model,
                    json.at("/usage/input_tokens").asLong(0), json.at("/usage/output_tokens").asLong(0),
                    (System.nanoTime() - start) / 1_000_000, 0.0);
        } catch (Exception e) { throw new IllegalStateException("Anthropic invocation failed: " + e.getMessage(), e); }
    }
    public ModelResponse stream(ModelRequest request, Consumer<String> onToken) {
        ModelResponse response = generate(request); onToken.accept(response.content()); return response;
    }
}
