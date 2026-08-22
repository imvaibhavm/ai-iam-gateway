package com.aiguard.ai.gateway.agent;

public final class StepBudgetExceededException extends RuntimeException {
    public StepBudgetExceededException(int maximum) {
        super("Agent step budget exhausted (maximum=" + maximum + ")");
    }
}
