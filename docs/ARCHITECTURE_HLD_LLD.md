# AI Security Gateway — HLD and LLD

## Scope and architectural ownership

This service is an identity-aware AI security control plane. Spring Boot is the authoritative security plane. LangGraph orchestrates agent execution but never grants access. PostgreSQL is the security system of record. OpenTelemetry transports operational traces; LangSmith is optional observability and evaluation, not authorization or audit storage.

## High-level design

```mermaid
flowchart TB
    Human[Human User] --> UI[Next.js Web UI\nChat and Admin Console]
    IdP[OIDC Identity Provider\nAuth0 / Okta / Entra / Keycloak] --> UI
    UI -->|validated-provider JWT + request| API[Spring Boot AI Security Plane]

    subgraph Security[Authoritative AI Security Plane]
      API --> Identity[Identity and Tenant Resolution\nHuman / Service / Agent]
      Identity --> Input[Input Security\nPII / secrets / injection / masking]
      Input --> PDP[Policy Decision Point\nRBAC + ABAC + ReBAC + delegation]
      PDP --> Direct[Direct Chat Gateway]
      PDP --> AgentGateway[Agent Gateway]
      Direct --> Router[Policy-Aware Model Router]
      AgentGateway --> RuntimeClient[Agent Runtime Client]
      RuntimeClient --> Router
      AgentGateway --> ToolGateway[Tool / MCP Gateway]
      ToolGateway --> PDP
      Router --> Output[Output Inspection and Redaction]
      Output --> Audit[Audit and Security Events]
    end

    RuntimeClient <-->|trusted internal API| LangGraph[Python LangGraph Runtime]
    ToolGateway --> GitHub[GitHub Tools\nmock foundation]
    ToolGateway --> FutureTools[Slack / DB / SaaS / MCP]

    Router --> CF[Cloudflare Workers AI\nGemma 4]
    Router --> OR[OpenRouter\nFree Router]
    Router --> Gemini[Google Gemini]
    Router --> HF[Hugging Face]
    Router --> Ollama[Ollama\nlocal/private]

    Audit --> Postgres[(PostgreSQL / Neon)]
    Audit --> OTEL[Micrometer / OpenTelemetry]
    OTEL --> LangSmith[LangSmith optional]
    OTEL --> SIEM[JSON logs / SIEM exporter]
```

## Deployment view

```mermaid
flowchart LR
    Browser[Browser] -->|HTTPS| Vercel[Vercel\nNext.js]
    Vercel -->|HTTPS + JWT| Render[Render\nSpring Boot API]
    Render -->|JDBC TLS| Neon[(Neon PostgreSQL)]
    Render -->|HTTPS| Cloudflare[Cloudflare Workers AI]
    Render -->|HTTPS| OpenRouter[OpenRouter]
    Render -->|HTTPS| Gemini[Gemini]
    Render -->|HTTPS| HF[Hugging Face]
    Render -. local profile .-> Ollama[Local Ollama]
    Render -. internal API .-> AgentRuntime[LangGraph Runtime]
    Render -. OTLP .-> Collector[OTEL Collector]
    Collector -. optional .-> LangSmith[LangSmith]
```

## Hosted OIDC trust boundary

```mermaid
flowchart LR
    IdP[Generic OIDC Provider] -->|Authorization Code + PKCE| SPA[Next.js SPA]
    SPA -->|Bearer access token| ResourceServer[Spring Security Resource Server]
    ResourceServer -->|signature + expiry + issuer + audience valid| Mapper[Configurable claims mapper]
    Mapper --> Resolver[IdentityResolver]
    Resolver --> Users[(PostgreSQL / Neon app_users)]
    Users -->|authoritative tenant, enabled, role| Context[IdentityContext]
    Context --> PDP[ABAC / ReBAC / PDP]
```

OIDC claims provide authenticated identity attributes, not authorization decisions. The first hosted login binds one pre-provisioned, unambiguous email record to the validated `(issuer, subject)`. Cross-tenant assertions, disabled users and unknown mappings fail closed. Production SPA access tokens remain in SDK-managed memory and are never persisted by this application.

Production provider priority is `cloudflare, openrouter, gemini, huggingface, ollama`. Only healthy, policy-eligible providers participate. Local-only obligations exclude every cloud provider. Local development explicitly uses Ollama first.

## Direct-chat request sequence

```mermaid
sequenceDiagram
    actor User
    participant UI as Next.js
    participant Auth as JWT Security
    participant Chat as ChatService
    participant DLP as PII/Secret Detector
    participant PDP as PolicyEngine
    participant Router as PolicyAwareModelRouter
    participant Provider as ModelProvider
    participant Output as OutputInspector
    participant DB as PostgreSQL Audit

    User->>UI: Submit message
    UI->>Auth: POST /api/chat/stream + Bearer JWT
    Auth->>Chat: Server-derived IdentityContext
    Chat->>DLP: Detect and mask input
    DLP-->>Chat: masked prompt + entity types
    Chat->>PDP: Identity + intent + classification
    PDP-->>Chat: ALLOW/DENY + obligations
    alt denied
      Chat->>DB: Persist denial
      Chat-->>UI: Safe denial event
    else allowed
      Chat->>Router: Select eligible ordered providers
      loop policy-approved fallbacks
        Router->>Provider: ModelRequest with masked prompt
        Provider-->>Router: response or failure
      end
      Router-->>Chat: selected/fallback response metadata
      Chat->>Output: Inspect and redact
      Chat->>DB: Persist provider, model, route, tokens, latency and policy
      Chat-->>UI: token + done SSE events
    end
```

### PII and secrets

```mermaid
flowchart TD
    Prompt --> Detect{Deterministic detection}
    Detect -->|No sensitive entity| Normal[Normal classification and policy]
    Detect -->|PII| Mask[Replace with stable placeholders]
    Mask --> Verify[Policy: MASK_INPUT + INSPECT_OUTPUT]
    Verify --> Cloud[Approved provider priority\nCloudflare first]
    Detect -->|Secret / credential| SecretMask[Mask secret]
    SecretMask --> LocalOnly[REQUIRE_LOCAL_MODEL]
    LocalOnly --> LocalAvailable{Healthy private provider?}
    LocalAvailable -->|Yes| Ollama[Private Ollama]
    LocalAvailable -->|No| FailClosed[Safe failure; no cloud fallback]
```

Raw prompts, credentials, JWTs and unmasked PII must not enter telemetry or audit content. Audit stores classifications and metadata, not secret values.

## Provider-routing LLD

```mermaid
classDiagram
    class ModelProvider {
      +providerId() String
      +modelId() String
      +cloud() boolean
      +health() ProviderHealth
      +generate(ModelRequest) ModelResponse
      +stream(ModelRequest, Consumer) ModelResponse
    }
    class ProviderRegistry
    class PolicyAwareModelRouter
    class ProviderExecutor
    class OpenAiCompatibleProvider
    class CloudflareWorkersAiProvider
    class OpenRouterProvider
    class GeminiProvider
    class HuggingFaceClient
    class OllamaClient

    ModelProvider <|.. OpenAiCompatibleProvider
    OpenAiCompatibleProvider <|-- CloudflareWorkersAiProvider
    OpenAiCompatibleProvider <|-- OpenRouterProvider
    OpenAiCompatibleProvider <|-- GeminiProvider
    ModelProvider <|.. HuggingFaceClient
    ModelProvider <|.. OllamaClient
    ProviderRegistry o-- ModelProvider
    PolicyAwareModelRouter --> ProviderRegistry
    ProviderExecutor --> ModelProvider
```

Routing algorithm:

1. Derive local-only restrictions from policy obligations and secret intent.
2. Remove providers that violate local/cloud restrictions.
3. Remove providers whose configured health is unavailable.
4. Sort eligible providers by configured priority.
5. Execute the first provider.
6. Use ordered fallbacks only when policy and `ALLOW_CLOUD_FALLBACK` permit them.
7. Circuit-break repeatedly failing providers.
8. Persist the actual provider and model, including fallback reason.

## Agent and approval flow

```mermaid
stateDiagram-v2
    [*] --> Created
    Created --> Reasoning
    Reasoning --> ToolProposed
    ToolProposed --> Denied: PDP DENY
    ToolProposed --> Executing: PDP ALLOW
    ToolProposed --> WaitingApproval: REQUIRE_APPROVAL
    WaitingApproval --> Rejected: human rejects
    WaitingApproval --> Revalidating: human approves
    Revalidating --> Denied: policy changed / scope lost
    Revalidating --> Executing: authorization still valid
    Executing --> Sanitizing
    Sanitizing --> Reasoning
    Reasoning --> Completed: final response
    Reasoning --> StepBudgetExceeded: max steps reached
    Denied --> Reasoning
    Rejected --> [*]
    Completed --> [*]
    StepBudgetExceeded --> [*]
```

Every agent tool proposal follows registry lookup, argument DLP, scope check, tenant check, PDP evaluation, risk decision, optional persistent approval, execution and result sanitization. Approval never overrides policy; authorization is re-run before execution.

The demonstration repository is `imvaibhavm/ai-iam-gateway`. `github.mergePullRequest` is HIGH risk and requires an ADMIN approval. The current handler is deterministic/mock and does not merge a real pull request. The Admin Console links approval records to the repository/PR and retains decision history.

## Persistence model

```mermaid
erDiagram
    APP_USERS {
      string id PK
      string tenant_id
      string email
      string role
      boolean enabled
    }
    AUDIT_LOG {
      string id PK
      string request_id
      string tenant_id
      string user_email
      string role
      string intent
      boolean allowed
      string provider
      string model
      string routing_reason
      long latency_ms
      long input_tokens
      long output_tokens
      decimal estimated_cost_usd
      string policy_version
      boolean provider_succeeded
      boolean output_redacted
    }
    AGENT_RUN {
      string id PK
      string request_id
      string tenant_id
      string agent_id
      string originating_subject
      string effective_scopes
      int step_count
      int max_steps
      string status
      string provider
      string policy_result
    }
    AGENT_APPROVAL {
      string id PK
      string run_id FK
      string request_id
      string tenant_id
      string tool_name
      string sanitized_arguments
      string risk
      string status
      string decided_by
      datetime decided_at
    }
    AGENT_RUN ||--o{ AGENT_APPROVAL : requires
    APP_USERS ||--o{ AUDIT_LOG : produces
```

Every query and record is tenant-scoped. PostgreSQL is authoritative for security audit and approval state. In-memory security-event summaries and LangSmith traces are operational views only.

## Observability hierarchy

```text
ai.request
├── ai.identity.resolve
├── ai.security.input_scan
├── ai.security.mask
├── ai.intent.classify
├── ai.policy.evaluate
├── ai.model.route
├── ai.model.inference
├── ai.security.output_scan
└── ai.audit.persist

ai.agent.run
├── ai.agent.step
├── ai.model.inference
├── ai.tool.propose
├── ai.tool.policy
├── ai.tool.execute
└── ai.agent.response
```

Safe correlation fields are `requestId`, `traceId` and `tenantId`. Trace identifiers are not authorization identifiers.

## Trust boundaries and failure rules

- Browser identity, tenant, role, clearance and agent claims are never trusted directly.
- JWT validation and server-side user lookup create the authoritative identity context.
- Cross-tenant access, delegation escalation and missing scopes fail closed.
- A model proposal has no authorization meaning.
- Approval-required tools cannot execute before approval and must be reauthorized afterward.
- Provider fallback cannot violate residency, local-only or other policy obligations.
- An unavailable required local model produces a safe failure, never a cloud fallback.
- Provider keys remain server-side environment secrets.
- PostgreSQL failure prevents authoritative audit-dependent operations from silently succeeding.

## Current limitations

- The direct chat UI sends only the latest user message; full multi-turn conversation history is not yet forwarded to providers.
- The SSE endpoint buffers provider output for final inspection, so the browser request remains pending until the inspected response is emitted.
- Ollama health currently reflects configuration rather than a remote connectivity probe.
- GitHub tools are deterministic mocks; a production GitHub App adapter and scoped installation token are still required.
- The development email-token endpoint is not production authentication and must be replaced by OIDC.
- LangSmith export is optional and must not be treated as the audit system of record.
