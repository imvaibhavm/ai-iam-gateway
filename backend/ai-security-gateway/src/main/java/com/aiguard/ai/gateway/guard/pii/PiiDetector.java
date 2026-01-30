package com.aiguard.ai.gateway.guard.pii;

public interface PiiDetector {
    PiiResult detectAndMask(String text);
}
