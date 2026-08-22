package com.aiguard.ai.gateway.tool;

public record ToolResult(String requestId, String toolName, String content,
                         boolean resultRedacted, String policyReason) { }
