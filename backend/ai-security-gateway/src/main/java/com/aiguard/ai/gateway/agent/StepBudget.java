package com.aiguard.ai.gateway.agent;

import java.util.concurrent.atomic.AtomicInteger;

/** Per-run guard against unbounded agent loops and tool invocation storms. */
public final class StepBudget {
    private final int maximum;
    private final AtomicInteger consumed = new AtomicInteger();

    public StepBudget(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        this.maximum = maximum;
    }

    public int consume() {
        int step = consumed.incrementAndGet();
        if (step > maximum) {
            consumed.decrementAndGet();
            throw new StepBudgetExceededException(maximum);
        }
        return step;
    }

    public int consumed() { return consumed.get(); }
    public int remaining() { return maximum - consumed.get(); }
    public int maximum() { return maximum; }
}
