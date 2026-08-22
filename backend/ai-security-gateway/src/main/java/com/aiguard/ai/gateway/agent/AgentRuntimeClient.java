package com.aiguard.ai.gateway.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Component
public class AgentRuntimeClient {
    private final HttpClient http; private final ObjectMapper json; private final String baseUrl;
    private final String token; private final Duration timeout;
    public AgentRuntimeClient(ObjectMapper json, @Value("${agent-runtime.base-url}") String baseUrl,
                              @Value("${agent-runtime.shared-token}") String token,
                              @Value("${agent-runtime.timeout-seconds:60}") long seconds) {
        this.json=json; this.baseUrl=baseUrl; this.token=token; this.timeout=Duration.ofSeconds(seconds);
        // Uvicorn does not support Java's clear-text HTTP/2 upgrade. Force HTTP/1.1 so request
        // bodies are not replayed/lost after a rejected h2c upgrade.
        this.http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3)).build();
    }
    public Map<String,Object> start(String runId) { return post("/runs", Map.of("runId", runId)); }
    public Map<String,Object> resume(String runId, boolean approved) {
        return post("/runs/"+runId+"/resume", Map.of("approved", approved));
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> post(String path, Object body) {
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(timeout)
                    .header("Content-Type","application/json").header("X-Agent-Runtime-Token",token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()/100 != 2) throw new IllegalStateException("agent_runtime_http_"+response.statusCode());
            return json.readValue(response.body(), Map.class);
        } catch (Exception e) { throw new IllegalStateException("agent_runtime_unavailable", e); }
    }
}
