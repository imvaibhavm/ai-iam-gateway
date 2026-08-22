package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.guard.intent.*;
import com.aiguard.ai.gateway.guard.policy.*;
import com.aiguard.ai.gateway.identity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PolicyContextTest {
    private final PolicyEngine policy = new PolicyEngine();
    private final IntentClassification general = new IntentClassification(IntentType.GENERAL, 1, "test", "N/A");

    @Test void agentMustHaveDelegatorAndActionScope() {
        IdentityContext missingDelegator = agent(null, Set.of("llm.generate"), Map.of());
        assertEquals("non_human_identity_requires_delegation",
                policy.evaluate(PolicyContext.llm(missingDelegator, general, DataClassification.INTERNAL)).reason());

        IdentityContext missingScope = agent("human-1", Set.of("tool.read"), Map.of());
        assertEquals("delegation_scope_missing",
                policy.evaluate(PolicyContext.llm(missingScope, general, DataClassification.INTERNAL)).reason());

        IdentityContext valid = agent("human-1", Set.of("llm.generate"), Map.of());
        assertTrue(policy.evaluate(PolicyContext.llm(valid, general, DataClassification.INTERNAL)).allowed());
    }

    @Test void tenantAndRelationshipBoundariesAreEnforced() {
        IdentityContext identity = new IdentityContext("user-1", "u@example.com", "tenant-a", UserRole.ENGINEER);
        PolicyResource otherTenant = PolicyResource.tenantResource("document", "doc-1", "tenant-b");
        PolicyContext context = new PolicyContext(identity, general, "read", otherTenant,
                DataClassification.INTERNAL, null, BigDecimal.ZERO, Map.of());
        assertEquals("relationship_access_denied", policy.evaluate(context).reason());

        PolicyResource owned = new PolicyResource("document", "doc-2", "tenant-a", "other-user", Map.of());
        context = new PolicyContext(identity, general, "read", owned,
                DataClassification.INTERNAL, null, BigDecimal.ZERO, Map.of());
        assertEquals("relationship_access_denied", policy.evaluate(context).reason());
    }

    @Test void claimBackedRelationshipCanGrantScopedRead() {
        IdentityContext viewer = new IdentityContext("user-1", "u@example.com", "tenant-a", UserRole.ENGINEER,
                IdentityType.HUMAN, null, Set.of(), Map.of("relationship:doc-2", "viewer"));
        PolicyResource resource = new PolicyResource("document", "doc-2", "tenant-a", "other-user", Map.of());
        PolicyContext context = new PolicyContext(viewer, general, "read", resource,
                DataClassification.INTERNAL, null, BigDecimal.ZERO, Map.of());
        assertTrue(policy.evaluate(context).allowed());
    }

    @Test void restrictedDataProducesEnforceableObligations() {
        IdentityContext identity = new IdentityContext("admin", "a@example.com", "tenant-a", UserRole.ADMIN);
        PolicyContext context = new PolicyContext(identity, general, "llm.generate", null,
                DataClassification.RESTRICTED, null, new BigDecimal("0.05"), Map.of());
        var decision = policy.evaluate(context);
        assertTrue(decision.allowed());
        Set<PolicyObligation.Type> types = new HashSet<>();
        decision.obligations().forEach(value -> types.add(value.type()));
        assertTrue(types.contains(PolicyObligation.Type.REQUIRE_LOCAL_MODEL));
        assertTrue(types.contains(PolicyObligation.Type.MASK_INPUT));
        assertTrue(types.contains(PolicyObligation.Type.INSPECT_OUTPUT));
        assertTrue(types.contains(PolicyObligation.Type.RECORD_AUDIT));
        assertTrue(types.contains(PolicyObligation.Type.LIMIT_COST));
    }

    @Test void residencyAttributeIsAnAbacBoundary() {
        IdentityContext identity = new IdentityContext("user-1", "u@example.com", "tenant-a", UserRole.ENGINEER,
                IdentityType.HUMAN, null, Set.of(), Map.of("region", "eu"));
        PolicyContext context = new PolicyContext(identity, general, "llm.generate", null,
                DataClassification.INTERNAL, "us", BigDecimal.ZERO, Map.of());
        assertEquals("residency_mismatch", policy.evaluate(context).reason());
    }

    private IdentityContext agent(String delegatedBy, Set<String> scopes, Map<String, String> attributes) {
        return new IdentityContext("agent-1", "agent@example.com", "tenant-a", UserRole.ENGINEER,
                IdentityType.AGENT, delegatedBy, scopes, attributes);
    }
}
