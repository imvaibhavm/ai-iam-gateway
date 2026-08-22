package com.aiguard.ai.gateway.provider.http;

import com.aiguard.ai.gateway.provider.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.function.Consumer;

public abstract class OpenAiCompatibleProvider implements ModelProvider {
    private final String id, token, model, baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    protected OpenAiCompatibleProvider(String id, String token, String model, String baseUrl, int timeoutSeconds) {
        this.id = id; this.token = token; this.model = model; this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
    }
    public String providerId() { return id; }
    public String modelId() { return model; }
    public boolean cloud() { return true; }
    public ProviderHealth health() { return token == null || token.isBlank() ? ProviderHealth.down(id + " token missing") : ProviderHealth.up(); }

    public ModelResponse generate(ModelRequest request) {
        long start = System.nanoTime();
        try {
            String body = "{\"model\":" + mapper.writeValueAsString(model) + ",\"messages\":[{\"role\":\"user\",\"content\":"
                    + mapper.writeValueAsString(request.prompt()) + "}],\"max_tokens\":" + request.maxTokens() + "}";
            HttpRequest http = request(body);
            HttpResponse<String> response = client.send(http, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException(id + " HTTP " + response.statusCode());
            JsonNode json = mapper.readTree(response.body());
            String content = json.at("/choices/0/message/content").asText();
            long in = json.at("/usage/prompt_tokens").asLong(0), out = json.at("/usage/completion_tokens").asLong(0);
            return new ModelResponse(content, id, model, in, out, elapsed(start), 0.0);
        } catch (Exception e) { throw new IllegalStateException(id + " invocation failed: " + e.getMessage(), e); }
    }

    public ModelResponse stream(ModelRequest request, Consumer<String> onToken) {
        ModelResponse response = generate(request);
        onToken.accept(response.content());
        return response;
    }

    protected HttpRequest request(String body) {
        return HttpRequest.newBuilder().uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
