package com.aiguard.ai.gateway.provider;

public record ModelResponse(String content, String provider, String model, long inputTokens,
                            long outputTokens, long latencyMs, double estimatedCostUsd) { }
