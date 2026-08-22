package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.guard.intent.IntentClassification;
import com.aiguard.ai.gateway.guard.intent.IntentType;
import com.aiguard.ai.gateway.guard.policy.PolicyDecision;
import com.aiguard.ai.gateway.guard.policy.*;
import com.aiguard.ai.gateway.identity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PolicyEngine {
    private final DelegationPolicy delegationPolicy;
    private final RelationshipAuthorizer relationships;

    public PolicyEngine() {
        this(new DelegationPolicy(), new TenantRelationshipAuthorizer());
    }

    @Autowired
    public PolicyEngine(DelegationPolicy delegationPolicy, RelationshipAuthorizer relationships) {
        this.delegationPolicy = delegationPolicy;
        this.relationships = relationships;
    }

    public PolicyDecision evaluate(UserRole role, IntentClassification ic) {
        IdentityContext identity = new IdentityContext("legacy", "legacy@local", "default", role);
        return evaluate(PolicyContext.llm(identity, ic, classification(ic.intent())));
    }

    public PolicyDecision evaluate(PolicyContext context) {
        if (context == null || context.identity() == null || context.intent() == null) {
            return PolicyDecision.deny("invalid_policy_context");
        }
        IdentityContext identity = context.identity();
        if (identity.tenantId() == null || identity.tenantId().isBlank()) {
            return PolicyDecision.deny("tenant_required");
        }
        DelegationPolicy.Result delegation = delegationPolicy.evaluate(identity, context.action());
        if (!delegation.allowed()) return PolicyDecision.deny(delegation.reason());
        if (!relationships.canAccess(identity, context.action(), context.resource())) {
            return PolicyDecision.deny("relationship_access_denied");
        }
        String permittedRegion = identity.attributes().get("region");
        if (permittedRegion != null && context.requestedRegion() != null
                && !permittedRegion.equalsIgnoreCase(context.requestedRegion())) {
            return PolicyDecision.deny("residency_mismatch");
        }

        IntentType intent = context.intent().intent();

        // Universal block (all roles)
        if (intent == IntentType.SECURITY || intent == IntentType.PROMPT_INJECTION) {
            return PolicyDecision.deny("blocked_security_intent");
        }

        // Admin can do everything
        if (identity.role() == UserRole.ADMIN) {
            return allowWithObligations("admin_allow_all", context);
        }

        // INTERN restrictions
        if (identity.role() == UserRole.INTERN) {
            if (intent == IntentType.FINANCE) return PolicyDecision.deny("intern_block_finance");
            if (intent == IntentType.HR) return PolicyDecision.deny("intern_block_hr");
            if (intent == IntentType.SECRETS) return PolicyDecision.deny("intern_block_secrets");
        }

        // FINANCE restrictions
        if (identity.role() == UserRole.FINANCE) {
            if (intent == IntentType.HR) return PolicyDecision.deny("finance_block_hr");
        }

        // ENGINEER restrictions
        if (identity.role() == UserRole.ENGINEER) {
            if (intent == IntentType.HR) return PolicyDecision.deny("engineer_block_hr");
        }

        return allowWithObligations("default_allow", context);
    }

    private PolicyDecision allowWithObligations(String reason, PolicyContext context) {
        java.util.List<PolicyObligation> obligations = new java.util.ArrayList<>();
        obligations.add(PolicyObligation.of(PolicyObligation.Type.RECORD_AUDIT));
        obligations.add(PolicyObligation.of(PolicyObligation.Type.INSPECT_OUTPUT));
        if (context.dataClassification() == DataClassification.CONFIDENTIAL
                || context.dataClassification() == DataClassification.RESTRICTED) {
            obligations.add(PolicyObligation.of(PolicyObligation.Type.MASK_INPUT));
        }
        if (context.dataClassification() == DataClassification.RESTRICTED) {
            obligations.add(PolicyObligation.of(PolicyObligation.Type.REQUIRE_LOCAL_MODEL));
        }
        if (context.estimatedCostUsd().signum() > 0) {
            obligations.add(new PolicyObligation(PolicyObligation.Type.LIMIT_COST,
                    Map.of("estimatedUsd", context.estimatedCostUsd().toPlainString())));
        }
        return new PolicyDecision(true, reason, obligations);
    }

    private static DataClassification classification(IntentType intent) {
        return switch (intent) {
            case PII, SECRETS -> DataClassification.RESTRICTED;
            case HR, FINANCE -> DataClassification.CONFIDENTIAL;
            default -> DataClassification.INTERNAL;
        };
    }
}
