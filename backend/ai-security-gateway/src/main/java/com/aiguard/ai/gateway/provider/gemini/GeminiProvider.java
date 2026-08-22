package com.aiguard.ai.gateway.provider.gemini;

import com.aiguard.ai.gateway.provider.http.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiProvider extends OpenAiCompatibleProvider {
    public GeminiProvider(@Value("${gemini.token:}") String token, @Value("${gemini.model}") String model,
                          @Value("${gemini.baseUrl}") String baseUrl,
                          @Value("${gateway.provider-timeout-seconds:45}") int timeout) {
        super("gemini", token, model, baseUrl, timeout);
    }
}
