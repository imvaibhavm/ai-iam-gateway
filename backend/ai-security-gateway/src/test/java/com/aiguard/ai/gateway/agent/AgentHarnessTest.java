package com.aiguard.ai.gateway.agent;

import com.aiguard.ai.gateway.guard.output.OutputInspector;
import com.aiguard.ai.gateway.guard.pii.RegexPiiDetector;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.identity.IdentityContext;
import com.aiguard.ai.gateway.tool.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentHarnessTest {
    @Test
    void stopsAgentAfterConfiguredStepBudget() {
        ToolHandler handler = new ToolHandler() {
            public ToolDescriptor descriptor() {
                return new ToolDescriptor("echo", Set.of(UserRole.ENGINEER), Set.of("acme"), false, false);
            }
            public Object execute(Map<String, Object> arguments) { return arguments.get("value"); }
        };
        var detector = new RegexPiiDetector();
        var gateway = new ToolGateway(new ToolRegistry(List.of(handler)), new ArgumentInspector(detector),
                new ToolPolicyEngine(), request -> ApprovalDecision.APPROVED, new OutputInspector(detector));
        var harness = new AgentHarness(gateway);
        var context = new AgentRunContext("run-1",
                new IdentityContext("user-1", "engineer@example.com", "acme", UserRole.ENGINEER),
                new StepBudget(2));

        assertEquals("one", harness.invokeTool(context, "echo", Map.of("value", "one")).content());
        assertEquals("two", harness.invokeTool(context, "echo", Map.of("value", "two")).content());
        assertThrows(StepBudgetExceededException.class,
                () -> harness.invokeTool(context, "echo", Map.of("value", "three")));
        assertEquals(2, context.stepBudget().consumed());
    }
}
