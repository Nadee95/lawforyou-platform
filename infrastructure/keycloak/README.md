# Keycloak Realm Configuration

## Files

| File | Purpose |
|---|---|
| `realm-export.json` | Full realm definition imported on first boot via `--import-realm` |

## Realm: `lawforyou`

| Setting | Value |
|---|---|
| Admin UI | http://localhost:8180 (admin / admin) |
| Realm | `lawforyou` |
| Token endpoint | `http://localhost:8180/realms/lawforyou/protocol/openid-connect/token` |
| JWKS URI | `http://localhost:8180/realms/lawforyou/protocol/openid-connect/certs` |
| OIDC Discovery | `http://localhost:8180/realms/lawforyou/.well-known/openid-configuration` |

## Client: `lawforyou-backend`

| Setting | Value |
|---|---|
| Client ID | `lawforyou-backend` |
| Client Secret | `lawforyou-backend-secret` (dev only — change in production) |
| Grant types | `password` (Phase 2 login proxy), `client_credentials` (Admin API), `authorization_code` (future frontend) |

## Custom JWT Claims (Protocol Mappers)

| Claim | Type | Source | Purpose |
|---|---|---|---|
| `tenant_id` | String | User attribute `tenant_id` | Multi-tenancy — downstream `TenantResolutionFilter` uses this |
| `user_id` | String | User attribute `user_id` | Maps Keycloak user to PostgreSQL `users.id` |
| `roles` | String[] | Realm roles | Authorization — `@PreAuthorize("hasAuthority(...)")` |
| `preferred_username` | String | Username | Logging / display |

## Dev Users

| User | Password | Role | tenant_id |
|---|---|---|---|
| `admin@lawforyou.dev` | `Admin@12345` | ADMIN | `00000000-0000-0000-0000-000000000001` |
| `lawyer@lawforyou.dev` | `Lawyer@12345` | LAWYER | `00000000-0000-0000-0000-000000000001` |
| `client@lawforyou.dev` | `Client@12345` | CLIENT | `00000000-0000-0000-0000-000000000001` |

## How Import Works

Keycloak starts with `start-dev --import-realm`. On first boot it reads all `*.json` files
from `/opt/keycloak/data/import/` (mounted from this directory).

**If the realm already exists in the database, the import is skipped automatically.**
To re-import after changes: delete the realm in the Admin UI → restart the container.

## Phase Roadmap

| Phase | Change |
|---|---|
| ✅ Phase 1-2 (now) | Infrastructure + realm + dev users |
| Phase 3 | API Gateway dual-token validation (Keycloak RS256 + legacy HS256 fallback) |
| Phase 4 | user-service calls Keycloak Admin API on register/login/logout |
| Phase 5 | case-service, document-service → OAuth2 Resource Servers |
| Phase 6 | `nadeex-spring-security:0.3.0` with `KeycloakJwtAuthenticationConverter` |
| Phase 7 | Remove HS256 fallback and legacy JWT library |

