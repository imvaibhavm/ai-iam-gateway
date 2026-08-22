package com.aiguard.ai.gateway.tool;

import com.aiguard.ai.gateway.identity.IdentityContext;

public record ApprovalRequest(String requestId, IdentityContext identity, String toolName,
                              String reason, String sanitizedArgumentSummary) { }
