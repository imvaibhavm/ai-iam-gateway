# Friday AI Security Control Plane — Low-Level Design

> Implementation baseline: 27 August 2026.

## 1. Component map

```mermaid
flowchart LR
    subgraph Web[Next.js frontend]
      Login[Login page]
      Chat[Chat page]
      Admin[Admin console]
    end

    subgraph API[Spring Boot API]
      Security[Generic OIDC JWT resource server]
      Identity[IdentityResolver]
      ChatController[ChatController]
      AgentController[Agent controllers]
      AdminControllers[Admin controllers]
      ChatService[ChatService]
      DLP[RegexPiiDetector]
      Classifier[RuleBasedIntentClassifier]
      PDP[PolicyEngine]
      Router[PolicyAwareModelRouter]
      Executor[ProviderExecutor]
      Output[OutputInspector]
      ToolGateway[ToolGateway]
      Audit[AuditService]
    end

    subgraph Providers[ModelProvider implementations]
      CF[CloudflareWorkersAiProvider]
      OR[OpenRouterProvider]
      Gemini[GeminiProvider]
      HF[HuggingFaceClient]
      Ollama[OllamaClient]
      OpenAI[OpenAiProvider]
      Anthropic[AnthropicProvider]
    end

    subgraph Agent[Python agent runtime]
      FastAPI[FastAPI endpoints]
      Graph[LangGraph workflow]
      State[AgentState]
      GatewayClient[SecurityPlaneClient]
    end

    Login --> Security
    Chat --> ChatController
    Admin --> AdminControllers
    Security --> Identity
    ChatController --> ChatService
    ChatService --> DLP --> Classifier --> PDP --> Router --> Executor
    Executor --> Providers
    ChatService --> Output --> Audit
    AgentController <--> GatewayClient
    GatewayClient <--> Graph
    Graph --> State
    AgentController --> ToolGateway
```

## 2. Backend package responsibilities

| Package | Responsibility | Key types |
|---|---|---|
| `identity` | Normalize validated OIDC claims, reconcile them to server-authoritative local identity and preserve delegation context | `ExternalIdentityClaimsMapper`, `IdentityResolver`, `IdentityContext`, `DelegationChain`, `DevTokenController` |
| `iam` | Users, roles, ABAC/ReBAC checks and authoritative policy evaluation | `PolicyEngine`, `RelationshipAuthorizer`, `AppUserService` |
| `guard.pii` | Deterministic PII, secret and credential detection plus masking | `RegexPiiDetector`, `PiiResult`, `PiiEntity` |
| `guard.intent` | Deterministic workload/domain classification | `RuleBasedIntentClassifier`, `IntentClassification` |
| `guard.policy` | Policy request, resource, decision and obligation value types | `PolicyContext`, `PolicyDecision`, `PolicyObligation` |
| `routing` | Filter and order policy-eligible providers | `PolicyAwareModelRouter`, `RoutingDecision` |
| `provider` | Provider-neutral inference contracts, registry, fallback execution and circuit state | `ModelProvider`, `ProviderRegistry`, `ProviderExecutor` |
| `guard.output` | Inspect and redact model output before client delivery | `OutputInspector` |
| `chat` | Direct chat HTTP/SSE API and security pipeline orchestration | `ChatController`, `ChatService` |
| `agent` | Agent-run persistence, internal callbacks and approval lifecycle | `AgentExecutionService`, `AgentRun`, `AgentApproval` |
| `tool` | Tool registry, tenant/scope/DLP enforcement, approvals and result sanitization | `ToolGateway`, `ToolPolicyEngine`, `ToolRegistry` |
| `audit` | Durable PostgreSQL security audit | `AuditLog`, `AuditService`, `AuditController` |
| `observability` | Safe metrics, observations and pluggable security-event export | `AiTelemetry`, `SecurityEventPublisher` |
| `usage` | Rate limits and token-budget reservations | `RateLimitService`, `UsageBudgetService` |

## 3. Identity model

```mermaid
classDiagram
    class IdentityContext {
      +subject String
      +email String
      +tenantId String
      +role UserRole
      +type IdentityType
      +delegatedBy String
      +scopes Set~String~
      +groups Set~String~
      +attributes Map~String,String~
    }
    class DelegationChain {
      +originator String
      +currentActor String
      +grants List~Grant~
      +validate() Validation
    }
    class Grant {
      +subject String
      +delegatedBy String
      +tenantId String
      +scopes Set~String~
      +expiresAt Instant
    }
    IdentityContext --> DelegationChain
    DelegationChain o-- Grant
```

Identity invariants:

1. The JWT must already be cryptographically validated by Spring Security.
2. Tenant, role and identity type are reconstructed server-side.
3. Database role overrides a stale asserted role during chat processing.
4. Non-human actors require a delegator and the requested action scope.
5. Every child delegation scope set must be a subset of its parent.
6. Cross-tenant delegation is denied.

### Hosted OIDC resolution

```text
Bearer access token
  -> Spring signature/expiry/issuer/audience validation
  -> lookup durable (issuer, subject) binding
  -> if bound: load authoritative local user directly
  -> if unbound: configured email/UserInfo bootstrap
  -> require exactly one enabled local user
  -> persist durable binding
  -> construct IdentityContext
```

For an already-bound subject, mutable email claims are not used as the authorization key. For first-login bootstrap, UserInfo must return the same `sub` as the validated access token. Database tenant, role and enabled state always override external assertions.

## 4. Direct-chat API

### `POST /api/chat`

Authenticated JSON request/response endpoint.

```json
{
  "sessionId": "s1",
  "messages": [
    { "role": "user", "content": "Hello" }
  ]
}
```

### `POST /api/chat/stream`

Authenticated SSE endpoint using the same request contract.

```text
event:token
data:Safe inspected response

event:done
data:[DONE]
```

The current implementation extracts the final user message. A future conversation service must validate and forward bounded history rather than trusting an arbitrary client transcript.

### Chat pipeline pseudocode

```text
identity = IdentityResolver.require(authentication)
user = AppUserService.requireEnabled(identity.tenant, identity.email)
rateLimit.check(identity.tenant, identity.subject)

pii = RegexPiiDetector.detectAndMask(message)
safePrompt = pii.maskedText
intent = pii.hasPii ? PII : classifier.classify(safePrompt)
classification = classifyData(intent)
decision = policy.evaluate(identity, intent, classification)

if DENY:
    persist audit
    return safe denial

reservation = usage.reserve(tenant, safePrompt)
routing = router.select(intent, decision)
response = executor.execute(routing, ModelRequest(safePrompt, budget))
safeOutput = outputInspector.inspect(response)
persist authoritative audit
return safeOutput
```

## 5. Input-security behavior

```mermaid
flowchart TD
    Raw[Raw user input] --> Regex[Deterministic detector]
    Regex --> Entities[Typed entities with offsets]
    Entities --> Masker[Stable placeholder replacement]
    Masker --> SafePrompt[Masked prompt]
    SafePrompt --> Intent[Intent classification]
    Intent --> DataClass[Data classification]
    DataClass --> PDP[Policy evaluation]
```

Examples:

```text
8542098418                  -> [PHONE_1]
person@example.com          -> [EMAIL_1]
12.12.12.123                -> [IP_ADDRESS_1]
recognized API credential   -> [API_KEY_1]
```

PII receives `MASK_INPUT` and `INSPECT_OUTPUT`; approved cloud inference is permitted after deterministic masking. Secrets receive `REQUIRE_LOCAL_MODEL` and cannot use a cloud fallback.

## 6. Policy decision point

```mermaid
classDiagram
    class PolicyContext {
      +identity IdentityContext
      +intent IntentClassification
      +action String
      +resource PolicyResource
      +dataClassification DataClassification
      +requestedRegion String
      +estimatedCostUsd BigDecimal
    }
    class PolicyDecision {
      +effect Effect
      +risk Risk
      +policyVersion String
      +reason String
      +obligations List~PolicyObligation~
    }
    class PolicyObligation {
      +type Type
      +parameters Map
    }
    PolicyContext --> PolicyDecision
    PolicyDecision o-- PolicyObligation
```

Evaluation order:

1. Validate context and tenant.
2. Validate delegation and effective scope.
3. Enforce tenant/ReBAC access.
4. Enforce residency attributes.
5. Deny security and prompt-injection intent.
6. Apply deterministic role/domain restrictions.
7. Attach masking, inspection, local-model, cost and audit obligations.

Policy outcomes must be deterministic and testable offline. Model output never changes an authorization decision.

Canonical decision example:

```json
{
  "effect": "ALLOW",
  "risk": "MEDIUM",
  "policyVersion": "2026-08-23.4",
  "reason": "default_allow",
  "obligations": [
    { "type": "MASK_INPUT", "parameters": {} },
    { "type": "INSPECT_OUTPUT", "parameters": {} },
    { "type": "DISABLE_MEMORY", "parameters": {} },
    { "type": "LIMIT_OUTPUT_TOKENS", "parameters": { "tokens": "800" } },
    { "type": "MAX_COST_USD", "parameters": { "usd": "0.01" } }
  ]
}
```

RAG, model routing, agents, tools, memory and usage controls must consume this contract rather than creating subsystem-specific allow/deny languages.

## 7. Provider subsystem

### Contract

```java
public interface ModelProvider {
    String providerId();
    String modelId();
    boolean cloud();
    ProviderHealth health();
    ModelResponse generate(ModelRequest request);
    ModelResponse stream(ModelRequest request, Consumer<String> onToken);
}
```

### Provider implementations

| Provider ID | Adapter | Default model |
|---|---|---|
| `cloudflare` | OpenAI-compatible Workers AI endpoint | `@cf/google/gemma-4-26b-a4b-it` |
| `openrouter` | OpenAI-compatible endpoint | `openrouter/free` |
| `gemini` | Google OpenAI-compatible endpoint | `gemini-3.6-flash` |
| `huggingface` | Dedicated HF streaming adapter | `mistralai/Mistral-7B-Instruct-v0.2:featherless-ai` |
| `ollama` | Ollama native HTTP adapter | `llama3.2:1b` |
| `openai` | OpenAI-compatible endpoint | configured OpenAI model |
| `anthropic` | Anthropic message adapter | configured Claude model |

### Routing algorithm

```mermaid
flowchart TD
    Start[Intent + PolicyDecision] --> Local{Local-only obligation or secret?}
    Local -->|Yes| FilterLocal[Remove cloud providers]
    Local -->|No| All[Consider all registered providers]
    FilterLocal --> Healthy[Filter configured health]
    All --> Healthy
    Healthy --> Order[Sort by MODEL_PROVIDER_PRIORITY]
    Order --> First[Execute first provider]
    First --> Success{Success and non-empty output?}
    Success -->|Yes| Return[Return response metadata]
    Success -->|No| Circuit[Record provider failure]
    Circuit --> Allowed{Fallback permitted by policy/config?}
    Allowed -->|Yes| Next[Execute next eligible provider]
    Next --> Success
    Allowed -->|No| Fail[Fail closed]
```

Default production order:

```text
cloudflare -> openrouter -> gemini -> huggingface -> ollama
```

Actual provider, model, fallback reason, latency, token counts, cost and success status are persisted.

## 8. Agent execution APIs

| Endpoint | Caller | Purpose |
|---|---|---|
| `POST /api/agent/runs` | Authenticated UI | Create an agent execution |
| `GET /api/admin/agent-runs` | ADMIN | Tenant-scoped run list |
| `GET /api/admin/approvals` | ADMIN | Pending approvals |
| `GET /api/admin/approvals/history` | ADMIN | Approval audit history |
| `POST /api/admin/approvals/{id}/approve` | ADMIN | Approve and resume |
| `POST /api/admin/approvals/{id}/reject` | ADMIN | Reject and resume safely |
| Internal agent endpoints | LangGraph runtime | Context, inference, tool proposal and approved execution |

## 9. Agent state and graph

```text
requestId
tenantId
agentId
originatingSubject
delegationChain
effectiveScopes
messages
stepCount
maxSteps
pendingToolCall
approvalState
securityContext
```

```mermaid
flowchart TD
    Start --> Reason
    Reason --> Tool{Tool proposed?}
    Tool -->|No| Final[Final response]
    Tool -->|Yes| Gateway[Spring Tool Gateway]
    Gateway --> Decision{PDP decision}
    Decision -->|DENY| Denied[Return denied result]
    Denied --> Reason
    Decision -->|REQUIRE_APPROVAL| Persist[Persist pending approval]
    Persist --> Interrupt[LangGraph interrupt]
    Interrupt --> Resume[Human decision]
    Resume --> Recheck[Re-run authorization]
    Recheck -->|DENY| Denied
    Recheck -->|ALLOW| Execute[Execute approved tool]
    Decision -->|ALLOW| Execute
    Execute --> Sanitize[Sanitize tool result]
    Sanitize --> Reason
    Final --> End
```

## 10. Tool gateway

```mermaid
sequenceDiagram
    participant Agent
    participant Registry as ToolRegistry
    participant DLP as ArgumentInspector
    participant Scope as Scope/Tenant checks
    participant PDP as ToolPolicyEngine
    participant Approval as Approval store
    participant Handler as ToolHandler
    participant Sanitize as Result sanitizer

    Agent->>Registry: propose(name, arguments)
    Registry->>DLP: inspect arguments
    DLP->>Scope: sanitized arguments
    Scope->>PDP: identity + descriptor + resource
    alt deny
      PDP-->>Agent: DENY
    else approval required
      PDP->>Approval: persist PENDING
      Approval-->>Agent: interrupt
    else allow
      PDP->>Handler: execute
      Handler->>Sanitize: raw result
      Sanitize-->>Agent: safe result
    end
```

Mock GitHub tools:

| Tool | Action | Risk | Scope | Approval |
|---|---|---|---|---|
| `github.searchCode` | SEARCH | LOW | `github.read` | No |
| `github.readFile` | READ | LOW | `github.read` | No |
| `github.mergePullRequest` | WRITE | HIGH | `github.write` | Required |

The current merge handler is a deterministic mock. A real implementation should use a GitHub App installation token scoped to the selected repository; browser-supplied tokens must never be accepted.

## 11. Persistence

```mermaid
erDiagram
    APP_USERS ||--o{ AUDIT_LOG : creates
    AGENT_RUN ||--o{ AGENT_APPROVAL : requires

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
      string intent
      boolean allowed
      string provider
      string model
      string routing_reason
      boolean provider_succeeded
      boolean output_redacted
      string policy_version
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
    }
    AGENT_APPROVAL {
      string id PK
      string run_id FK
      string tenant_id
      string request_id
      string tool_name
      string sanitized_arguments
      string risk
      string status
      string decided_by
      datetime decided_at
    }
```

Tenant ID is mandatory on users, audits, agent runs and approvals. Admin reads are tenant-scoped.

## 12. Telemetry

`AiTelemetry` centralizes safe Micrometer observations. Recommended spans:

```text
ai.request
├── ai.security.input_scan
├── ai.intent.classify
├── ai.policy.evaluate
├── ai.model.route
├── ai.model.inference
└── ai.audit.persist

ai.agent.run
├── ai.agent.step
├── ai.tool.policy
├── ai.tool.execute
└── ai.agent.response
```

Allowed attributes include request ID, tenant ID, identity type, intent, classification, policy decision/version, provider/model, routing reason, tool metadata, token counts, cost and redaction flags. Prompts, credentials, JWTs and raw PII are prohibited.

## 13. Configuration contract

```env
DATABASE_URL=
DATABASE_USER=
DATABASE_PASSWORD=
JWT_SECRET=
JWT_ISSUER=ai-security-gateway
DEV_TOKEN_ENABLED=false

OIDC_ENABLED=true
OIDC_ISSUER_URI=https://YOUR_TENANT.example/
OIDC_AUDIENCE=https://api.example
OIDC_USERINFO_URI=https://YOUR_TENANT.example/userinfo
OIDC_EMAIL_CLAIM=email
OIDC_EMAIL_VERIFIED_CLAIM=email_verified
OIDC_REQUIRE_VERIFIED_EMAIL=true
OIDC_GROUPS_CLAIM=groups
OIDC_TENANT_CLAIM=https://aiguard.example/tenant_id
OIDC_DEPARTMENT_CLAIM=https://aiguard.example/department
OIDC_CLEARANCE_CLAIM=https://aiguard.example/clearance
CORS_ALLOWED_ORIGINS=https://YOUR_FRONTEND.example

MODEL_PROVIDER_PRIORITY=cloudflare,openrouter,gemini,huggingface,ollama
ALLOW_CLOUD_FALLBACK=true
DEFAULT_TOKEN_BUDGET=512
PROVIDER_TIMEOUT_SECONDS=45

CLOUDFLARE_API_TOKEN=
CLOUDFLARE_ACCOUNT_ID=
CLOUDFLARE_MODEL=@cf/google/gemma-4-26b-a4b-it
OPENROUTER_API_KEY=
OPENROUTER_MODEL=openrouter/free
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.6-flash
HF_TOKEN=

AGENT_RUNTIME_URL=
AGENT_RUNTIME_TOKEN=
AI_OBSERVABILITY_ENABLED=false
AI_OBSERVABILITY_LANGSMITH_ENABLED=false
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=
```

All values that authenticate or authorize access are server-side secrets and must be supplied through environment/secret management, never committed.

## 14. Error behavior

| Failure | Required behavior |
|---|---|
| Invalid/expired JWT | `401`; frontend should clear session and return to login |
| Tenant resolution failure | Reject request |
| PDP failure | Deny protected action |
| PII detected | Mask, inspect output, use policy-approved provider |
| Secret detected | Mask and require private/local model |
| Required local provider unavailable | Safe failure; no cloud fallback |
| Cloud provider failure | Ordered fallback only when permitted |
| Empty provider completion | Treat as provider failure |
| Tool registry failure | Do not execute |
| Approval store failure | Do not execute approval-required tool |
| Changed policy after approval | Reauthorization denial |
| Audit exporter failure | Preserve durable request behavior; meter exporter failure |

## 15. Known implementation gaps

1. Direct chat does not yet pass bounded multi-turn history to `ModelProvider`.
2. SSE can release provider chunks before final inspection; incremental rolling-buffer DLP or risk-based full buffering is required.
3. Providers need scheduled connectivity/capability health probes and persisted circuit state.
4. Provider selection does not yet optimize across region, capability, measured quality, cost and latency.
5. GitHub execution is mock-only.
6. Development email JWT login must remain disabled in hosted/production OIDC profiles.
7. The agent runtime uses a single-runtime SQLite LangGraph checkpointer; a production PostgreSQL checkpointer and runtime HA are required.
8. `DISABLE_MEMORY` is represented as an obligation but no production memory subsystem enforces it yet.
9. RAG ACL filtering, real MCP credential vending, tamper-evident audit and packaged VPC/on-prem enforcement nodes are not implemented.

## 16. Administration and MCP governance

The admin UI has two primary information surfaces:

- **Dashboard** — tenant KPIs, model availability, agent activity, approval decisions, MCP availability and user access administration.
- **Logs** — the tenant-scoped, authoritative request decision table.

`GET /api/admin/audit/metrics?period=24h|7d|30d` reads at most 10,000 audit records for the authenticated administrator's tenant and returns allow/deny totals, PII counts, output redactions and time buckets. The database index `ix_audit_tenant_ts` supports this access path.

`POST /api/admin/users/import` accepts at most 250 reviewed users. Email, role, attribute length, policy syntax and duplicate emails are validated before `saveAll`. The request cannot specify the tenant. Locally administered department, clearance, region and policy assignments override external attributes when building `IdentityContext`.

The MCP catalog has two enforcement levels:

```text
Tenant administrator enables catalog item
        -> user may opt in
        -> agent request may name only opted-in items
        -> Tool Gateway/PDP remains authoritative at execution time
```

`mcp_provider_settings` stores tenant availability and `user_mcp_settings` stores user selection. These records do not contain provider credentials. Current catalog entries are integration foundations; real external transports, OAuth and scoped credential vending remain separate production work.
