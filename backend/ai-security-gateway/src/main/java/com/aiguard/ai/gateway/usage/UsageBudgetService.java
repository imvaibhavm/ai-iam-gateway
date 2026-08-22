package com.aiguard.ai.gateway.usage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UsageBudgetService {
    private final int defaultBudget;
    public UsageBudgetService(@Value("${gateway.default-token-budget:4096}") int defaultBudget) { this.defaultBudget = defaultBudget; }
    public Reservation reserve(String tenantId, String prompt) {
        long estimatedInput = Math.max(1, (prompt == null ? 0 : prompt.length()) / 4);
        if (estimatedInput >= defaultBudget) throw new IllegalStateException("Token budget exceeded");
        return new Reservation(estimatedInput, defaultBudget - (int) estimatedInput);
    }
    public record Reservation(long estimatedInputTokens, int maxOutputTokens) { }
}
