package com.aiguard.ai.gateway.tool;

import com.aiguard.ai.gateway.guard.pii.PiiDetector;
import com.aiguard.ai.gateway.guard.pii.PiiType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ArgumentInspector {
    private final PiiDetector piiDetector;

    public ArgumentInspector(PiiDetector piiDetector) { this.piiDetector = piiDetector; }

    public Inspection inspect(Map<String, Object> arguments) {
        String flattened = String.valueOf(arguments);
        var result = piiDetector.detectAndMask(flattened);
        boolean secret = result.entities().stream()
                .anyMatch(entity -> entity.type() == PiiType.API_KEY || entity.type() == PiiType.JWT);
        return new Inspection(result.hasPii(), secret, result.maskedText());
    }

    public record Inspection(boolean sensitive, boolean secret, String sanitizedSummary) { }
}
