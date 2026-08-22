package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.guard.intent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineTest {
    private final PolicyEngine policy = new PolicyEngine();
    private IntentClassification intent(IntentType type) { return new IntentClassification(type, 1, "test", "N/A"); }

    @Test void universalThreatsAreDeniedForEveryRole() {
        for (UserRole role : UserRole.values()) {
            assertFalse(policy.evaluate(role, intent(IntentType.SECURITY)).allowed());
            assertFalse(policy.evaluate(role, intent(IntentType.PROMPT_INJECTION)).allowed());
        }
    }
    @Test void adminCanUseNonThreatIntents() {
        assertTrue(policy.evaluate(UserRole.ADMIN, intent(IntentType.HR)).allowed());
        assertTrue(policy.evaluate(UserRole.ADMIN, intent(IntentType.SECRETS)).allowed());
    }
    @Test void roleRestrictionsAreDeterministic() {
        assertFalse(policy.evaluate(UserRole.INTERN, intent(IntentType.FINANCE)).allowed());
        assertFalse(policy.evaluate(UserRole.INTERN, intent(IntentType.HR)).allowed());
        assertFalse(policy.evaluate(UserRole.ENGINEER, intent(IntentType.HR)).allowed());
        assertFalse(policy.evaluate(UserRole.FINANCE, intent(IntentType.HR)).allowed());
        assertTrue(policy.evaluate(UserRole.FINANCE, intent(IntentType.FINANCE)).allowed());
    }
}
