package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.guard.intent.IntentClassification;
import com.aiguard.ai.gateway.guard.intent.IntentType;
import com.aiguard.ai.gateway.guard.policy.PolicyDecision;
import com.aiguard.ai.gateway.guard.policy.*;
import com.aiguard.ai.gateway.identity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@Component
public class PolicyEngine {
    private final DelegationPolicy delegationPolicy;
    private final RelationshipAuthorizer relationships;
    private final String policyVersion;

    public PolicyEngine() {
        this(new DelegationPolicy(), new TenantRelationshipAuthorizer(), "2026-08-23.4");
    }

    @Autowired
    public PolicyEngine(DelegationPolicy delegationPolicy, RelationshipAuthorizer relationships,
                        @Value("${gateway.policy-version:2026-08-23.4}") String policyVersion) {
        this.delegationPolicy = delegationPolicy;
        this.relationships = relationships;
        this.policyVersion = policyVersion;
    }

    public PolicyDecision evaluate(UserRole role, IntentClassification ic) {
        IdentityContext identity = new IdentityContext("legacy", "legacy@local", "default", role);
        return evaluate(PolicyContext.llm(identity, ic, classification(ic.intent())));
    }

    public PolicyDecision evaluate(PolicyContext context) {
        if (context == null || context.identity() == null || context.intent() == null) {
            return deny("invalid_policy_context", PolicyDecision.Risk.HIGH);
        }
        IdentityContext identity = context.identity();
        if (identity.tenantId() == null || identity.tenantId().isBlank()) {
            return deny("tenant_required", PolicyDecision.Risk.HIGH);
        }
        DelegationPolicy.Result delegation = delegationPolicy.evaluate(identity, context.action());
        if (!delegation.allowed()) return deny(delegation.reason(), PolicyDecision.Risk.HIGH);
        if (!relationships.canAccess(identity, context.action(), context.resource())) {
            return deny("relationship_access_denied", PolicyDecision.Risk.HIGH);
        }
        String permittedRegion = identity.attributes().get("region");
        if (permittedRegion != null && context.requestedRegion() != null
                && !permittedRegion.equalsIgnoreCase(context.requestedRegion())) {
            return deny("residency_mismatch", PolicyDecision.Risk.HIGH);
        }

        IntentType intent = context.intent().intent();

        // Universal block (all roles)
        if (intent == IntentType.SECURITY || intent == IntentType.PROMPT_INJECTION) {
            return deny("blocked_security_intent", PolicyDecision.Risk.CRITICAL);
        }

        // Admin can do everything
        if (identity.role() == UserRole.ADMIN) {
            return allowWithObligations("admin_allow_all", context);
        }

        // INTERN restrictions
        if (identity.role() == UserRole.INTERN) {
            if (intent == IntentType.FINANCE) return deny("intern_block_finance", PolicyDecision.Risk.HIGH);
            if (intent == IntentType.HR) return deny("intern_block_hr", PolicyDecision.Risk.HIGH);
            if (intent == IntentType.SECRETS) return deny("intern_block_secrets", PolicyDecision.Risk.CRITICAL);
        }

        // FINANCE restrictions
        if (identity.role() == UserRole.FINANCE) {
            if (intent == IntentType.HR) return deny("finance_block_hr", PolicyDecision.Risk.HIGH);
        }

        // ENGINEER restrictions
        if (identity.role() == UserRole.ENGINEER) {
            if (intent == IntentType.HR) return deny("engineer_block_hr", PolicyDecision.Risk.HIGH);
        }

        return allowWithObligations("default_allow", context);
    }

    private PolicyDecision allowWithObligations(String reason, PolicyContext context) {
        java.util.List<PolicyObligation> obligations = new java.util.ArrayList<>();
        obligations.add(PolicyObligation.of(PolicyObligation.Type.RECORD_AUDIT));
        obligations.add(PolicyObligation.of(PolicyObligation.Type.INSPECT_OUTPUT));
        obligations.add(PolicyObligation.outputTokenLimit(800));
        obligations.add(PolicyObligation.maxCostUsd("0.01"));
        if (context.dataClassification() == DataClassification.CONFIDENTIAL
                || context.dataClassification() == DataClassification.RESTRICTED) {
            obligations.add(PolicyObligation.of(PolicyObligation.Type.MASK_INPUT));
        }
        // Deterministically detected PII is masked before policy evaluation and may use an
        // approved cloud provider. Secrets remain local-only and fail closed.
        if (context.dataClassification() == DataClassification.RESTRICTED
                && context.intent().intent() != IntentType.PII) {
            obligations.add(PolicyObligation.of(PolicyObligation.Type.REQUIRE_LOCAL_MODEL));
        }
        if (context.dataClassification() == DataClassification.RESTRICTED) {
            obligations.add(PolicyObligation.of(PolicyObligation.Type.DISABLE_MEMORY));
        }
        if (context.estimatedCostUsd().signum() > 0) {
            obligations.add(new PolicyObligation(PolicyObligation.Type.LIMIT_COST,
                    Map.of("estimatedUsd", context.estimatedCostUsd().toPlainString())));
        }
        PolicyDecision.Risk risk = switch (context.dataClassification()) {
            case PUBLIC -> PolicyDecision.Risk.LOW;
            case INTERNAL -> PolicyDecision.Risk.MEDIUM;
            case CONFIDENTIAL -> PolicyDecision.Risk.HIGH;
            case RESTRICTED -> PolicyDecision.Risk.CRITICAL;
        };
        return PolicyDecision.allow(policyVersion, risk, reason, obligations);
    }

    private PolicyDecision deny(String reason, PolicyDecision.Risk risk) {
        return PolicyDecision.deny(policyVersion, risk, reason);
    }

    private static DataClassification classification(IntentType intent) {
        return switch (intent) {
            case PII, SECRETS -> DataClassification.RESTRICTED;
            case HR, FINANCE -> DataClassification.CONFIDENTIAL;
            default -> DataClassification.INTERNAL;
        };
    }
}
