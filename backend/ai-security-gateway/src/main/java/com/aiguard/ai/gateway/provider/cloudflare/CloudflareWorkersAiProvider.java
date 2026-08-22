package com.aiguard.ai.gateway.provider.cloudflare;

import com.aiguard.ai.gateway.provider.ProviderHealth;
import com.aiguard.ai.gateway.provider.http.OpenAiCompatibleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CloudflareWorkersAiProvider extends OpenAiCompatibleProvider {
    private final String token;
    private final String accountId;

    public CloudflareWorkersAiProvider(@Value("${cloudflare.token:}") String token,
            @Value("${cloudflare.account-id:}") String accountId,
            @Value("${cloudflare.model:@cf/google/gemma-4-26b-a4b-it}") String model,
            @Value("${gateway.provider-timeout-seconds:45}") int timeout) {
        super("cloudflare", token, model,
                "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/v1", timeout);
        this.token = token;
        this.accountId = accountId;
    }

    @Override public ProviderHealth health() {
        if (token == null || token.isBlank()) return ProviderHealth.down("cloudflare token missing");
        if (accountId == null || accountId.isBlank()) return ProviderHealth.down("cloudflare account id missing");
        return ProviderHealth.up();
    }
}
