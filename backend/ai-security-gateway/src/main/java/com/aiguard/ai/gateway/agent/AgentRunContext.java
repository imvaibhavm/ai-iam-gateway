package com.aiguard.ai.gateway.agent;

import com.aiguard.ai.gateway.identity.IdentityContext;

import java.util.Objects;

public record AgentRunContext(String runId, IdentityContext identity, StepBudget stepBudget) {
    public AgentRunContext {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(stepBudget, "stepBudget");
    }
}
