package com.aiguard.ai.gateway.tool;

public final class ToolAccessDeniedException extends RuntimeException {
    public ToolAccessDeniedException(String reason) { super(reason); }
}
