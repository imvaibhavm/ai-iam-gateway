package com.aiguard.ai.gateway.guard.pii;

public record PiiEntity(
        PiiType type,
        String original,
        int start,
        int end,
        String masked
) { }
