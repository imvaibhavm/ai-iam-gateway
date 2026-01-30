package com.aiguard.ai.gateway.debug;

import com.aiguard.ai.gateway.guard.pii.PiiDetector;
import com.aiguard.ai.gateway.guard.pii.PiiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/debug/pii")
@RequiredArgsConstructor
public class PiiDebugController {

    private final PiiDetector piiDetector;

    @PostMapping
    public PiiResult debug(@RequestBody PiiDebugRequest request) {
        return piiDetector.detectAndMask(request.text());
    }

    public record PiiDebugRequest(String text) {}
}
