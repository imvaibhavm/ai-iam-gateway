package com.aiguard.ai.gateway.guard.output;

import com.aiguard.ai.gateway.guard.pii.PiiDetector;
import org.springframework.stereotype.Component;

@Component
public class OutputInspector {
    private final PiiDetector piiDetector;
    public OutputInspector(PiiDetector piiDetector) { this.piiDetector = piiDetector; }
    public InspectionResult inspect(String output) {
        var result = piiDetector.detectAndMask(output);
        return new InspectionResult(result.maskedText(), result.hasPii(),
                result.hasPii() ? "output_sensitive_data_redacted" : "output_allowed");
    }
    public record InspectionResult(String safeOutput, boolean redacted, String reason) { }
}
