package com.aiguard.ai.gateway.huggingface;

import com.aiguard.ai.gateway.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
public class HuggingFaceClient implements LlmClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${huggingface.token:}")
    private String token;

    @Value("${huggingface.model}")
    private String model;

    @Value("${huggingface.baseUrl}")
    private String baseUrl;

    @Override
    public String generate(String userMessage) {
        try {
            if (token == null || token.isBlank()) {
                return "❌ HF_TOKEN missing. Set it as environment variable.";
            }

            String body = """
                    {
                      "model": %s,
                      "stream": false,
                      "messages": [
                        {"role":"system","content":"You are a helpful assistant."},
                        {"role":"user","content": %s}
                      ]
                    }
                    """.formatted(
                    mapper.writeValueAsString(model),
                    mapper.writeValueAsString(userMessage)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return "❌ HuggingFace error: " + response.body();
            }

            JsonNode json = mapper.readTree(response.body());
            JsonNode content = json.at("/choices/0/message/content");

            if (!content.isMissingNode()) {
                return content.asText();
            }

            return json.toString();

        } catch (Exception e) {
            return "❌ Failed calling HuggingFace: " + e.getMessage();
        }
    }

    @Override
    public void stream(String userMessage, Consumer<String> onToken) {

        if (token == null || token.isBlank()) {
            onToken.accept("❌ HF_TOKEN missing. Set it as environment variable.");
            return;
        }

        try {
            String body = """
                    {
                      "model": %s,
                      "stream": true,
                      "messages": [
                        {"role":"system","content":"You are a helpful assistant."},
                        {"role":"user","content": %s}
                      ]
                    }
                    """.formatted(
                    mapper.writeValueAsString(model),
                    mapper.writeValueAsString(userMessage)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                onToken.accept("❌ HuggingFace error: " + err);
                return;
            }

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // HF streaming is like OpenAI:
                    // data: {json}
                    // data: [DONE]

                    if (!line.startsWith("data:")) continue;

                    String data = line.substring("data:".length()).trim();

                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    // parse JSON chunk
                    JsonNode chunk = mapper.readTree(data);

                    // delta content path: choices[0].delta.content
                    JsonNode delta = chunk.at("/choices/0/delta/content");
                    if (!delta.isMissingNode()) {
                        String tokenPiece = delta.asText();
                        if (tokenPiece != null && !tokenPiece.isBlank()) {
                            onToken.accept(tokenPiece);
                        }
                    }
                }
            }

        } catch (Exception e) {
            onToken.accept("❌ Failed streaming HuggingFace: " + e.getMessage());
        }
    }
}
