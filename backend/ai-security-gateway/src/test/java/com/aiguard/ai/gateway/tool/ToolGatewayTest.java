package com.aiguard.ai.gateway.tool;

import com.aiguard.ai.gateway.guard.output.OutputInspector;
import com.aiguard.ai.gateway.guard.pii.RegexPiiDetector;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.identity.IdentityContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ToolGatewayTest {
    private final IdentityContext engineer =
            new IdentityContext("user-1", "engineer@example.com", "acme", UserRole.ENGINEER);

    @Test
    void enforcesRoleBeforeToolExecution() {
        AtomicBoolean executed = new AtomicBoolean();
        ToolHandler handler = handler(
                new ToolDescriptor("finance.write", Set.of(UserRole.FINANCE), Set.of("acme"), false, false),
                args -> { executed.set(true); return "ok"; });

        ToolGateway gateway = gateway(handler, request -> ApprovalDecision.APPROVED);
        var error = assertThrows(ToolAccessDeniedException.class,
                () -> gateway.execute(request("finance.write", Map.of("amount", 10))));

        assertEquals("role_not_authorized", error.getMessage());
        assertFalse(executed.get());
    }

    @Test
    void enforcesTenantScope() {
        ToolHandler handler = handler(
                new ToolDescriptor("repo.read", Set.of(UserRole.ENGINEER), Set.of("other"), false, false),
                args -> "ok");
        var error = assertThrows(ToolAccessDeniedException.class,
                () -> gateway(handler, request -> ApprovalDecision.APPROVED)
                        .execute(request("repo.read", Map.of())));
        assertEquals("tenant_not_authorized", error.getMessage());
    }

    @Test
    void blocksSecretsWithoutSendingThemToApprovalOrTool() {
        AtomicBoolean approvalCalled = new AtomicBoolean();
        AtomicBoolean executed = new AtomicBoolean();
        ToolHandler handler = handler(
                new ToolDescriptor("repo.read", Set.of(UserRole.ENGINEER), Set.of("acme"), false, true),
                args -> { executed.set(true); return "ok"; });

        var error = assertThrows(ToolAccessDeniedException.class, () -> gateway(handler, request -> {
            approvalCalled.set(true);
            return ApprovalDecision.APPROVED;
        }).execute(request("repo.read", Map.of("token", "hf_12345678901234567890"))));

        assertEquals("secret_in_tool_arguments", error.getMessage());
        assertFalse(approvalCalled.get());
        assertFalse(executed.get());
    }

    @Test
    void sensitiveArgumentsRequireApprovalAndApprovalSeesOnlyMaskedSummary() {
        AtomicBoolean summaryMasked = new AtomicBoolean();
        ToolHandler handler = handler(
                new ToolDescriptor("crm.lookup", Set.of(UserRole.ENGINEER), Set.of("acme"), false, true),
                args -> "customer found");

        ToolResult result = gateway(handler, request -> {
            summaryMasked.set(!request.sanitizedArgumentSummary().contains("person@example.com")
                    && request.sanitizedArgumentSummary().contains("[EMAIL_1]"));
            return ApprovalDecision.APPROVED;
        }).execute(request("crm.lookup", Map.of("email", "person@example.com")));

        assertTrue(summaryMasked.get());
        assertEquals("customer found", result.content());
    }

    @Test
    void sanitizesSensitiveToolResults() {
        ToolHandler handler = handler(
                new ToolDescriptor("directory.read", Set.of(UserRole.ENGINEER), Set.of("acme"), false, false),
                args -> "Contact person@example.com");

        ToolResult result = gateway(handler, request -> ApprovalDecision.APPROVED)
                .execute(request("directory.read", Map.of()));

        assertTrue(result.resultRedacted());
        assertFalse(result.content().contains("person@example.com"));
        assertTrue(result.content().contains("[EMAIL_1]"));
    }

    private ToolRequest request(String name, Map<String, Object> args) {
        return new ToolRequest("req-1", engineer, name, args);
    }

    private ToolGateway gateway(ToolHandler handler, ApprovalGate gate) {
        var detector = new RegexPiiDetector();
        return new ToolGateway(new ToolRegistry(List.of(handler)), new ArgumentInspector(detector),
                new ToolPolicyEngine(), gate, new OutputInspector(detector));
    }

    private ToolHandler handler(ToolDescriptor descriptor,
                                java.util.function.Function<Map<String, Object>, Object> function) {
        return new ToolHandler() {
            @Override public ToolDescriptor descriptor() { return descriptor; }
            @Override public Object execute(Map<String, Object> arguments) { return function.apply(arguments); }
        };
    }
}
