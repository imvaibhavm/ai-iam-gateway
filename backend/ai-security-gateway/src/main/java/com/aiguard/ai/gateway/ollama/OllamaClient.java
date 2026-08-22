package com.aiguard.ai.gateway.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.aiguard.ai.gateway.provider.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

@Component
public class OllamaClient implements ModelProvider {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ollama.baseUrl}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    @Override public String providerId() { return "ollama"; }
    @Override public String modelId() { return model; }
    @Override public boolean cloud() { return false; }
    @Override public ProviderHealth health() { return ProviderHealth.up(); }
    @Override public ModelResponse generate(ModelRequest request) {
        long start = System.nanoTime();
        String content = chat(request.prompt());
        if (content.startsWith("❌")) throw new IllegalStateException(content);
        return response(content, start);
    }
    @Override public ModelResponse stream(ModelRequest request, Consumer<String> onToken) {
        long start = System.nanoTime();
        StringBuilder full = new StringBuilder();
        chatStream(request.prompt(), token -> { full.append(token); onToken.accept(token); });
        if (full.toString().startsWith("❌")) throw new IllegalStateException(full.toString());
        return response(full.toString(), start);
    }
    private ModelResponse response(String content, long start) {
        return new ModelResponse(content, providerId(), model, 0, 0,
                (System.nanoTime() - start) / 1_000_000, 0.0);
    }


    public String chat(String userMessage) {
        try {
            String body = """
                    {
                      "model": "%s",
                      "messages": [
                        {"role": "system", "content": "You are a helpful assistant."},
                        {"role": "user", "content": %s}
                      ],
                      "stream": false
                    }
                    """.formatted(model, mapper.writeValueAsString(userMessage));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return "❌ Ollama error: " + response.body();
            }

            JsonNode json = mapper.readTree(response.body());
            return json.get("message").get("content").asText();

        } catch (Exception e) {
            return "❌ Failed calling Ollama: " + e.getMessage();
        }
    }

    public void chatStream(String userMessage, Consumer<String> onToken) {
        try {
            String body = """
                    {
                      "model": "%s",
                      "messages": [
                        {"role": "system", "content": "You are a helpful assistant."},
                        {"role": "user", "content": %s}
                      ],
                      "stream": true
                    }
                    """.formatted(model, mapper.writeValueAsString(userMessage));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                onToken.accept("❌ Ollama error: " + err);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // Each line is JSON with: message.content + done
                    JsonNode json = mapper.readTree(line);

                    // token chunk
                    JsonNode messageNode = json.get("message");
                    if (messageNode != null && messageNode.get("content") != null) {
                        String token = messageNode.get("content").asText();
                        if (token != null && !token.isBlank()) onToken.accept(token);
                    }

                    // end
                    if (json.has("done") && json.get("done").asBoolean()) {
                        break;
                    }
                }
            }

        } catch (Exception e) {
            onToken.accept("❌ Failed streaming Ollama: " + e.getMessage());
        }
    }
}
