package com.aiguard.ai.gateway.tool;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {
    private final Map<String, ToolHandler> handlers;

    public ToolRegistry(List<ToolHandler> handlers) {
        Map<String, ToolHandler> indexed = new HashMap<>();
        for (ToolHandler handler : handlers) {
            if (indexed.putIfAbsent(handler.descriptor().name(), handler) != null) {
                throw new IllegalStateException("Duplicate tool: " + handler.descriptor().name());
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    public ToolHandler require(String name) {
        ToolHandler handler = handlers.get(name);
        if (handler == null) throw new ToolAccessDeniedException("tool_not_registered");
        return handler;
    }
}
