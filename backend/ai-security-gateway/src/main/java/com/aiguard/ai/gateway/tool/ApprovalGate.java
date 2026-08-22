package com.aiguard.ai.gateway.tool;

@FunctionalInterface
public interface ApprovalGate {
    ApprovalDecision requestApproval(ApprovalRequest request);
}
