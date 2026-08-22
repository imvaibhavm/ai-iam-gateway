package com.aiguard.ai.gateway.tool;

import org.springframework.stereotype.Component;

/** Production-safe default until a human approval service is configured. */
@Component
public class DenyByDefaultApprovalGate implements ApprovalGate {
    @Override public ApprovalDecision requestApproval(ApprovalRequest request) {
        return ApprovalDecision.DENIED;
    }
}
