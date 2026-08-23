# AI IAM Gateway

An identity-aware AI security control plane. Auth0 (or another OIDC provider) authenticates humans; Spring Boot, PostgreSQL/Neon, `IdentityResolver`, ABAC/ReBAC and the PDP remain authoritative for authorization.

## Authentication architecture

```text
Auth0 / Okta / Entra / Keycloak
              ↓ signed access token
Spring Security OAuth2 Resource Server
              ↓ validated Jwt
ConfigurableOidcClaimsMapper
              ↓ authenticated attributes
IdentityResolver
              ↓ reconcile (issuer, subject, email)
PostgreSQL/Neon AppUser
              ↓ authoritative tenant, enabled status and role
IdentityContext → ABAC / ReBAC / PDP
```

The browser never supplies an authoritative role, tenant, clearance, groups, or identity type. External role assertions are ignored. Unknown, disabled, ambiguously mapped, cross-tenant, invalid-signature, expired, wrong-issuer and wrong-audience identities fail closed.

## Auth0 demo setup

1. In Auth0, create an **API** with identifier `https://api.aiguard.example` (or your chosen `OIDC_AUDIENCE`) and RS256 signing.
2. Create a **Single Page Application**. The frontend uses Authorization Code Flow with PKCE; it has no client secret.
3. Configure Allowed Callback URLs: `http://localhost:3000, https://YOUR_FRONTEND.example`.
4. Configure Allowed Logout URLs with the same origins.
5. Configure Allowed Web Origins with the same origins.
6. Enable Refresh Token Rotation if `offline_access` is used.
7. The frontend requests `openid profile email offline_access`. Add API permissions/scopes only when the backend actually enforces them.
8. Enable the API's offline access when the SPA requests `offline_access`. If a custom-API access token omits `email`, the backend resolves it from the provider's OIDC UserInfo endpoint and verifies that UserInfo returns the same `sub`; unverified or conflicting email identities fail closed. Optional Auth0 Actions may add namespaced claims for tenant, department and clearance, plus a configurable groups claim. Never map an Auth0 role directly to application access.
9. Pre-provision the person in `app_users`. On first validated login, an unambiguous email match is bound to `(external_issuer, external_subject)`; later requests use that durable binding. Run [`V3__oidc_external_identity.sql`](infra/migrations/V3__oidc_external_identity.sql) before deployment when schema auto-update is disabled.

Backend:

```env
SPRING_PROFILES_ACTIVE=demo
OIDC_ENABLED=true
OIDC_ISSUER_URI=https://YOUR_TENANT.us.auth0.com/
OIDC_AUDIENCE=https://api.aiguard.example
OIDC_USERINFO_URI=https://YOUR_TENANT.us.auth0.com/userinfo
OIDC_EMAIL_VERIFIED_CLAIM=email_verified
OIDC_REQUIRE_VERIFIED_EMAIL=true
DEV_TOKEN_ENABLED=false
CORS_ALLOWED_ORIGINS=https://YOUR_FRONTEND.example
```

Frontend:

```env
NEXT_PUBLIC_OIDC_ENABLED=true
NEXT_PUBLIC_AUTH0_DOMAIN=YOUR_TENANT.us.auth0.com
NEXT_PUBLIC_AUTH0_CLIENT_ID=YOUR_PUBLIC_SPA_CLIENT_ID
NEXT_PUBLIC_AUTH0_AUDIENCE=https://api.aiguard.example
NEXT_PUBLIC_APP_URL=https://YOUR_FRONTEND.example
NEXT_PUBLIC_BACKEND_URL=https://YOUR_BACKEND.example
```

The frontend SDK keeps production access tokens in memory, not `localStorage`. Only public SPA configuration uses `NEXT_PUBLIC_*`; there is no Auth0 client secret.

## Generic OIDC templates

Only issuer, audience and claim names change:

```env
# Okta
OIDC_ISSUER_URI=https://YOUR_ORG.okta.com/oauth2/default
OIDC_AUDIENCE=api://aiguard
OIDC_GROUPS_CLAIM=groups

# Microsoft Entra ID (use a tenant-specific issuer)
OIDC_ISSUER_URI=https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0
OIDC_AUDIENCE=YOUR_API_APPLICATION_CLIENT_ID
OIDC_EMAIL_CLAIM=preferred_username
OIDC_GROUPS_CLAIM=groups

# Keycloak
OIDC_ISSUER_URI=https://id.example.com/realms/aiguard
OIDC_AUDIENCE=aiguard-api
OIDC_EMAIL_CLAIM=email
OIDC_GROUPS_CLAIM=groups
```

## Local development

```env
SPRING_PROFILES_ACTIVE=local
OIDC_ENABLED=false
DEV_TOKEN_ENABLED=true
NEXT_PUBLIC_OIDC_ENABLED=false
```

OIDC and development JWT modes are intentionally mutually exclusive. The `demo` and `production` profiles require OIDC and disable development tokens. A missing OIDC issuer prevents startup instead of silently enabling development authentication.

## Verification

```bash
cd backend/ai-security-gateway && ./mvnw test
cd frontend && npm run lint && npm run build
```

Never store access tokens, refresh tokens, authorization codes, JWT bodies, provider keys or Auth0 credentials in PostgreSQL, logs, telemetry, Git, or browser local storage.
