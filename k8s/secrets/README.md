# Kubernetes Secrets — LawForYou Platform

Secrets are **not** managed by Helm (credentials never live in Helm values files or Git history).

---

## Quick start

```powershell
# 1. Edit the placeholder values at the top of the script
notepad .\scripts\create-secrets.ps1

# 2. Dry-run (prints what WOULD be created — no cluster changes)
.\scripts\create-secrets.ps1

# 3. Apply to the cluster
.\scripts\create-secrets.ps1 -Apply

# 4. Verify
kubectl get secrets -n lawforyou
```

---

## Secret reference

### `lawforyou-user-secret` — user-service

| Key | Description |
|-----|-------------|
| `POSTGRES_URL` | JDBC URL, e.g. `jdbc:postgresql://host:5432/userdb` |
| `POSTGRES_USER` | Database username |
| `POSTGRES_PASSWORD` | Database password |
| `REDIS_HOST` | Redis hostname (Kubernetes service name) |
| `JWT_SECRET` | HS256 signing secret (≥ 32 chars) |
| `KEYCLOAK_JWKS_URI` | `http://<keycloak>:8080/realms/<realm>/protocol/openid-connect/certs` |

### `lawforyou-case-secret` — case-service

| Key | Description |
|-----|-------------|
| `POSTGRES_URL` | JDBC URL |
| `POSTGRES_USER` | Database username |
| `POSTGRES_PASSWORD` | Database password |
| `JWT_SECRET` | HS256 signing secret |

### `lawforyou-doc-secret` — document-service

| Key | Description |
|-----|-------------|
| `MONGODB_URI` | e.g. `mongodb://host:27017/documents` |
| `MINIO_ENDPOINT` | e.g. `http://minio-svc:9000` |
| `MINIO_ACCESS_KEY` | MinIO / S3 access key |
| `MINIO_SECRET_KEY` | MinIO / S3 secret key |
| `JWT_SECRET` | HS256 signing secret |

### `lawforyou-gateway-secret` — api-gateway

| Key | Description |
|-----|-------------|
| `REDIS_HOST` | Redis hostname |
| `JWT_SECRET` | HS256 signing secret |
| `KEYCLOAK_JWKS_URI` | Keycloak JWKS endpoint |

### `lawforyou-comm-secret` — communication-service

| Key | Description |
|-----|-------------|
| `SMTP_HOST` | SMTP server hostname |
| `SMTP_PORT` | SMTP port (e.g. `587`) |
| `SMTP_USER` | SMTP username / email address |
| `SMTP_PASS` | SMTP password |
| `EMAIL_FROM` | Sender string, e.g. `LawForYou <no-reply@example.com>` |

---

## minikube default values

When running locally with the infrastructure Helm chart the service names are:

| Dependency | Kubernetes service name | Source |
|------------|------------------------|--------|
| PostgreSQL | `lawforyou-postgres` | `fullnameOverride: lawforyou-postgres` |
| Redis | `lawforyou-redis-master` | `fullnameOverride: lawforyou-redis` + standalone master suffix |
| MongoDB | `lawforyou-mongodb` | `fullnameOverride: lawforyou-mongodb` |
| MinIO | `lawforyou-minio` | `fullnameOverride: lawforyou-minio` |
| Keycloak | `keycloak` | deployed separately |

> Postgres databases created by initdb: **`user_db`** (user-service) and **`case_db`** (case-service).  
> Postgres username: **`lawforyou`** (matches `auth.username` in infra values).

---

## Updating a secret

```powershell
# Re-run the script — it is idempotent (uses kubectl apply)
.\scripts\create-secrets.ps1 -Apply

# Or patch a single key in-place
kubectl patch secret lawforyou-user-secret -n lawforyou `
  --type merge `
  -p '{\"stringData\":{\"JWT_SECRET\":\"new-value\"}}'
```

---

## GitHub Actions (CI/CD)

Store credentials as **GitHub Actions Secrets** and inject them at deploy time.

| GitHub Secret | Used by |
|---------------|---------|
| `POSTGRES_PASSWORD` | user-service, case-service |
| `JWT_SECRET` | all services |
| `KEYCLOAK_JWKS_URI` | user-service, api-gateway |
| `REDIS_HOST` | user-service, api-gateway |
| `MONGODB_URI` | document-service |
| `MINIO_ACCESS_KEY` | document-service |
| `MINIO_SECRET_KEY` | document-service |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASS` / `EMAIL_FROM` | communication-service |
| `KUBE_CONFIG` | base64 kubeconfig for `kubectl` access |
