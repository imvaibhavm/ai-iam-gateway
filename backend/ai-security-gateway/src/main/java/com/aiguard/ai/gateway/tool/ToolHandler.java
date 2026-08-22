package com.aiguard.ai.gateway.tool;

import java.util.Map;

public interface ToolHandler {
    ToolDescriptor descriptor();
    Object execute(Map<String, Object> arguments);
}
