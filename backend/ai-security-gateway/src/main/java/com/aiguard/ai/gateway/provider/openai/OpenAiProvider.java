package com.aiguard.ai.gateway.provider.openai;

import com.aiguard.ai.gateway.provider.http.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProvider extends OpenAiCompatibleProvider {
    public OpenAiProvider(@Value("${openai.token:}") String token, @Value("${openai.model}") String model,
                          @Value("${openai.baseUrl}") String baseUrl,
                          @Value("${gateway.provider-timeout-seconds:45}") int timeout) {
        super("openai", token, model, baseUrl, timeout);
    }
}
