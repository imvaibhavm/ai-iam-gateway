package com.aiguard.ai.gateway.provider.openrouter;

import com.aiguard.ai.gateway.provider.http.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterProvider extends OpenAiCompatibleProvider {
    public OpenRouterProvider(@Value("${openrouter.token:}") String token,
                              @Value("${openrouter.model:openrouter/free}") String model,
                              @Value("${openrouter.baseUrl:https://openrouter.ai/api/v1}") String baseUrl,
                              @Value("${gateway.provider-timeout-seconds:45}") int timeout) {
        super("openrouter", token, model, baseUrl, timeout);
    }
}
