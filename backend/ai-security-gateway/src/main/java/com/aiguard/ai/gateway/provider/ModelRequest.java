package com.aiguard.ai.gateway.provider;

import java.util.Map;

public record ModelRequest(String requestId, String prompt, int maxTokens, Map<String, Object> metadata) { }
