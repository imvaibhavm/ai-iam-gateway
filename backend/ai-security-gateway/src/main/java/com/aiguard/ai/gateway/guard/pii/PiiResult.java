package com.aiguard.ai.gateway.guard.pii;

import java.util.List;

public record PiiResult(
        String originalText,
        String maskedText,
        List<PiiEntity> entities
) {
    public boolean hasPii() {
        return entities != null && !entities.isEmpty();
    }
}
