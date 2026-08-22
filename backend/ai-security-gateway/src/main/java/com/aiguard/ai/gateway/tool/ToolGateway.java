package com.aiguard.ai.gateway.tool;

import com.aiguard.ai.gateway.guard.output.OutputInspector;
import org.springframework.stereotype.Service;

@Service
public class ToolGateway {
    private final ToolRegistry registry;
    private final ArgumentInspector argumentInspector;
    private final ToolPolicyEngine policyEngine;
    private final ApprovalGate approvalGate;
    private final OutputInspector outputInspector;

    public ToolGateway(ToolRegistry registry, ArgumentInspector argumentInspector,
                       ToolPolicyEngine policyEngine, ApprovalGate approvalGate,
                       OutputInspector outputInspector) {
        this.registry = registry;
        this.argumentInspector = argumentInspector;
        this.policyEngine = policyEngine;
        this.approvalGate = approvalGate;
        this.outputInspector = outputInspector;
    }

    public ToolResult execute(ToolRequest request) {
        ToolHandler handler = registry.require(request.toolName());
        var inspection = argumentInspector.inspect(request.arguments());
        var decision = policyEngine.evaluate(request, handler.descriptor(), inspection);
        if (!decision.allowed()) throw new ToolAccessDeniedException(decision.reason());
        if (decision.approvalRequired()) {
            var approval = new ApprovalRequest(request.requestId(), request.identity(), request.toolName(),
                    decision.reason(), inspection.sanitizedSummary());
            if (approvalGate.requestApproval(approval) != ApprovalDecision.APPROVED)
                throw new ToolAccessDeniedException("approval_denied");
        }
        return executeHandler(request, handler);
    }

    public Authorization authorize(ToolRequest request) {
        ToolHandler handler = registry.require(request.toolName());
        var inspection = argumentInspector.inspect(request.arguments());
        var decision = policyEngine.evaluate(request, handler.descriptor(), inspection);
        return new Authorization(decision.allowed(), decision.approvalRequired(), decision.reason(),
                inspection.sanitizedSummary(), handler.descriptor());
    }

    /** Called only after the caller verifies a persisted approval; policy is re-evaluated here. */
    public ToolResult executeApproved(ToolRequest request) {
        ToolHandler handler = registry.require(request.toolName());
        var inspection = argumentInspector.inspect(request.arguments());
        var decision = policyEngine.evaluate(request, handler.descriptor(), inspection);
        if (!decision.allowed()) throw new ToolAccessDeniedException(decision.reason());
        return executeHandler(request, handler);
    }

    private ToolResult executeHandler(ToolRequest request, ToolHandler handler) {
        Object raw = handler.execute(request.arguments());
        var safe = outputInspector.inspect(String.valueOf(raw));
        return new ToolResult(request.requestId(), request.toolName(), safe.safeOutput(),
                safe.redacted(), safe.reason());
    }

    public record Authorization(boolean allowed, boolean approvalRequired, String reason,
                                String sanitizedArguments, ToolDescriptor descriptor) {}
}
