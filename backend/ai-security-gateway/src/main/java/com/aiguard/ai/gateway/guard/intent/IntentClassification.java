package com.aiguard.ai.gateway.guard.intent;

public record IntentClassification(
        IntentType intent,
        double confidence,
        String reason,
        String rawModelOutput
) { }
