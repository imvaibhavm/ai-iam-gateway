package com.aiguard.ai.gateway.agent;

import com.aiguard.ai.gateway.tool.ToolGateway;
import com.aiguard.ai.gateway.tool.ToolRequest;
import com.aiguard.ai.gateway.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Small execution boundary used by future agent runtimes; it does not alter chat execution. */
@Service
public class AgentHarness {
    private final ToolGateway toolGateway;

    public AgentHarness(ToolGateway toolGateway) { this.toolGateway = toolGateway; }

    public ToolResult invokeTool(AgentRunContext context, String toolName, Map<String, Object> arguments) {
        int step = context.stepBudget().consume();
        return toolGateway.execute(new ToolRequest(
                context.runId() + ":" + step, context.identity(), toolName, arguments));
    }
}
