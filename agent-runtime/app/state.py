from typing import Any, TypedDict

class AgentState(TypedDict, total=False):
    requestId: str
    tenantId: str
    agentId: str
    originatingSubject: str
    delegationChain: list[str]
    effectiveScopes: list[str]
    messages: list[dict[str, str]]
    stepCount: int
    maxSteps: int
    pendingToolCall: dict[str, Any] | None
    approvalState: dict[str, Any] | None
    securityContext: dict[str, Any]
    toolCursor: int
    trajectory: list[str]
    toolResults: list[str]
    status: str
    response: str
    planned: bool
