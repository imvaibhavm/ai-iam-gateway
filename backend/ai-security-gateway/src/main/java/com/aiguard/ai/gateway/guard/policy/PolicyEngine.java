package com.aiguard.ai.gateway.guard.policy;

import com.aiguard.ai.gateway.guard.intent.IntentClassification;
import com.aiguard.ai.gateway.guard.intent.IntentType;
import com.aiguard.ai.gateway.iam.UserRole;

public class PolicyEngine {

    public PolicyDecision evaluate(UserRole role, IntentClassification ic) {

        IntentType intent = ic.intent();

        // universal blocks (all roles)
        if (intent == IntentType.SECURITY || intent == IntentType.PROMPT_INJECTION) {
            return PolicyDecision.deny("blocked_security_intent");
        }

        if (role == UserRole.ADMIN) {
            return PolicyDecision.allow("admin_allow_all");
        }

        // INTERN restrictions
        if (role == UserRole.INTERN) {
            if (intent == IntentType.FINANCE) return PolicyDecision.deny("intern_block_finance");
            if (intent == IntentType.HR) return PolicyDecision.deny("intern_block_hr");
            if (intent == IntentType.SECRETS) return PolicyDecision.deny("intern_block_secrets");
        }

        // FINANCE restrictions
        if (role == UserRole.FINANCE) {
            if (intent == IntentType.HR) return PolicyDecision.deny("finance_block_hr");
        }

        // ENGINEER restrictions
        if (role == UserRole.ENGINEER) {
            if (intent == IntentType.HR) return PolicyDecision.deny("engineer_block_hr");
        }

        return PolicyDecision.allow("default_allow");
    }
}
