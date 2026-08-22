package com.aiguard.ai.gateway.tool;

import com.aiguard.ai.gateway.identity.IdentityContext;

import java.util.Map;
import java.util.Objects;

public record ToolRequest(String requestId, IdentityContext identity, String toolName,
                          Map<String, Object> arguments) {
    public ToolRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(toolName, "toolName");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
