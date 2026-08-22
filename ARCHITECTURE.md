# AI Security Control Plane

## Execution and observability planes

- **Spring Boot is the AI Security Plane.** JWT identity, tenant boundaries, RBAC/ABAC/ReBAC,
  delegation, DLP, policy obligations, provider routing, tool authorization and PostgreSQL audits
  remain authoritative.
- **LangGraph is the Agent Execution Plane.** It owns workflow state, steps, checkpoints and
  interrupts. It receives a run ID, never a client-asserted role or tenant, and calls Spring for
  every inference and tool decision.
- **LangSmith is an optional AI Observability and Evaluation Plane.** It is not an authorization
  service or audit database.
- **PostgreSQL is the security system of record** for requests, agent runs and approvals.
- **OpenTelemetry is the vendor-neutral transport.** Spring emits content-free Micrometer/OTEL
  observations; an optional collector can export them to LangSmith or additional destinations.

The internal runtime API uses a server-to-server token. Spring reloads the persisted run before
every callback. SQLite checkpoints are durable for one development runtime; production should use
the LangGraph PostgreSQL checkpointer.

The gateway is provider-neutral and supports two execution paths:

1. Direct model requests through the policy-aware model router.
2. Agent executions through the agent harness and mediated tool gateway.

Every request is authenticated, assigned to a tenant, classified, evaluated by policy, constrained by decision obligations, and recorded as a security event. Provider, tool, memory, and delegated-agent access must never bypass the policy decision point.

## Trust boundaries

- JWT claims establish the human or workload identity.
- Agent identity is distinct from the initiating identity.
- Delegation grants narrow an identity's authority; they never expand it.
- Model providers receive only policy-approved and sanitized context.
- Tools are accessed through the tool gateway using scoped credentials.
- Tool arguments and results are inspected for sensitive information.
- Tenant identifiers are server-derived and included in persistence keys and audit queries.

## Execution sequence

```text
JWT identity -> request classification -> policy decision + obligations
    -> direct model router
    OR
    -> agent harness -> tool approval/authorization -> model router
    -> response inspection -> audit/security event -> client
```

## Deployment profiles

- Default/deployed: Hugging Face is preferred; development tokens are disabled.
- Local: Ollama is preferred; cloud fallback is disabled; development tokens are enabled.

Provider and tool fallback is fail-closed: only candidates present in the policy decision are eligible.

## Implemented modules

- `identity`: human, service, and agent JWT identities plus delegation chains and scopes.
- `guard.policy`: ABAC/ReBAC context, resources, data classifications, decisions, and obligations.
- `agent`: execution context and hard per-run step budgets.
- `tool`: registry, scoped authorization, argument DLP, approval gates, and result sanitization.
- `routing` and `provider`: policy-aware selection and isolated OpenAI, Anthropic, Gemini, Hugging Face, and Ollama adapters.
- `observability`: content-free security events, Micrometer observations/metrics, bounded tenant views, and pluggable SIEM exporters.

## Extension contracts

- Register a model by implementing `ModelProvider`.
- Register a tool or MCP capability by implementing `ToolHandler` and returning a restrictive `ToolDescriptor`.
- Integrate a human approval system by implementing `ApprovalGate`.
- Export to a SIEM, Langfuse bridge, or OTEL collector by implementing `SecurityEventExporter`.
- Integrate an enterprise IdP by configuring JWT issuer/JWKS validation and mapping its claims in `IdentityResolver`.
