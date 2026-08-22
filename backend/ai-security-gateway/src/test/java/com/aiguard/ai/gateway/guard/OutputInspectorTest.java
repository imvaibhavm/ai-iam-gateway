package com.aiguard.ai.gateway.guard;

import com.aiguard.ai.gateway.guard.output.OutputInspector;
import com.aiguard.ai.gateway.guard.pii.RegexPiiDetector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputInspectorTest {
    @Test void redactsSensitiveProviderOutput() {
        var result = new OutputInspector(new RegexPiiDetector()).inspect("Contact alice@example.com");
        assertTrue(result.redacted());
        assertFalse(result.safeOutput().contains("alice@example.com"));
    }
}
