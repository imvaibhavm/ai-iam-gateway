package com.aiguard.ai.gateway.observability;

import com.aiguard.ai.gateway.audit.entity.AuditLog;

import java.time.Instant;

/**
 * Provider-neutral, content-free security event suitable for OTEL logs or a SIEM.
 * Prompts and model responses are intentionally never included.
 */
public record SecurityEvent(
        String schemaVersion,
        String eventType,
        Instant timestamp,
        String auditId,
        String requestId,
        String tenantId,
        String actor,
        String role,
        String intent,
        boolean allowed,
        String decisionReason,
        String policyVersion,
        String provider,
        String model,
        String routingReason,
        boolean providerSucceeded,
        boolean outputRedacted,
        String piiTypes,
        long latencyMs,
        long inputTokens,
        long outputTokens,
        double estimatedCostUsd
) {
    public static SecurityEvent from(AuditLog log) {
        return new SecurityEvent(
                "1.0", "ai.policy.decision", log.getTs(), log.getId(), log.getRequestId(),
                log.getTenantId(), log.getUserEmail(), log.getRole() == null ? null : log.getRole().name(),
                log.getIntent(), log.isAllowed(), log.getDecisionReason(), log.getPolicyVersion(),
                log.getProvider(), log.getModel(), log.getRoutingReason(), log.isProviderSucceeded(),
                log.isOutputRedacted(), log.getPiiTypes(), log.getLatencyMs(), log.getInputTokens(),
                log.getOutputTokens(), log.getEstimatedCostUsd());
    }
}
