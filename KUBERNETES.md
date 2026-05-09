# Kubernetes / minikube Quick-Start

## Prerequisites

- [minikube](https://minikube.sigs.k8s.io/docs/start/) installed
- [kubectl](https://kubernetes.io/docs/tasks/tools/) installed
- [Helm 3](https://helm.sh/docs/intro/install/) installed
- [Docker](https://docs.docker.com/get-docker/) running
- GitHub PAT with `read:packages` scope (to pull images from ghcr.io)

---

## Step 1 — Start minikube

```bash
minikube start --cpus=4 --memory=8192 --driver=docker
minikube addons enable ingress
minikube addons enable metrics-server
```

---

## Step 2 — Create namespace

```bash
kubectl apply -f k8s/namespaces/lawforyou.yaml
```

---

## Option A — Build images locally into minikube *(recommended for local dev — no Docker push needed)*

This approach loads all images directly into minikube's internal Docker daemon, bypassing GHCR entirely.

### One-shot script (build + optional deploy)

```powershell
# Build images only
.\scripts\build-local.ps1

# Build images AND deploy all Helm charts in one go
.\scripts\build-local.ps1 -Deploy

# Target a custom namespace
.\scripts\build-local.ps1 -Deploy -Namespace lawforyou
```

The script:
1. Compiles all Java services with `./gradlew bootJar`
2. Points the Docker CLI at minikube (`minikube docker-env`)
3. Builds all 7 Docker images inside minikube (`imagePullPolicy: Never`)
4. Optionally runs `helm upgrade --install` with the `values-local.yaml` override for each chart

> **Skip Steps 3 and 6** when using Option A — the GHCR pull secret is not required and `install-all.ps1` is replaced by `build-local.ps1 -Deploy`.

---

## Step 3 — Create pull secret for GHCR *(skip when using Option A)*

```bash
kubectl create secret docker-registry lawforyou-ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=Nadee95 \
  --docker-password=<YOUR_GITHUB_PAT> \
  --namespace=lawforyou
```

---

## Step 4 — Create application secrets

See [`k8s/secrets/README.md`](k8s/secrets/README.md) for all `kubectl create secret` commands.

---

## Step 5 — Deploy infrastructure (PostgreSQL, Redis, Kafka, MongoDB)

```bash
cd lawforyou-platform/helm/infra
helm dependency update
helm upgrade --install lawforyou-infra . \
  --namespace lawforyou \
  --wait --timeout 10m
```

---

## Step 6 — Deploy all services

**Option A (local images, no registry):**
```powershell
# Windows PowerShell — builds JARs, loads images into minikube, then deploys
.\scripts\build-local.ps1 -Deploy
```

**Option B (pull from GHCR):**
```bash
# Linux / macOS
chmod +x scripts/install-all.sh
./scripts/install-all.sh

# Windows PowerShell
.\scripts\install-all.ps1
```

---

## Step 7 — Apply Ingress

```bash
kubectl apply -f k8s/ingress/lawforyou-ingress.yaml
```

Add to hosts file:
```
127.0.0.1  lawforyou.local
```

In a separate terminal:
```bash
minikube tunnel
```

---

## Verify

```bash
kubectl get pods -n lawforyou
kubectl get svc  -n lawforyou
curl http://lawforyou.local/api/auth/health
```

---

## Blue-Green Rollback

```bash
# Linux / macOS
./scripts/rollback.sh user-service

# Windows PowerShell
.\scripts\rollback.ps1 -Service user-service
```

---

## Check active slot for any service

```bash
helm get values user-service -n lawforyou -o json | jq '.activeSlot'
```

---

## GitHub Actions Secrets Required

| Secret | Description |
|--------|-------------|
| `KUBE_CONFIG` | Base64-encoded kubeconfig: `cat ~/.kube/config \| base64 -w0` |

The `GITHUB_TOKEN` secret is auto-provided by GitHub Actions — no manual setup needed.

---

## Testing / Monitoring APIs

### 1. Port-forward the API Gateway (quickest)

```powershell
kubectl port-forward svc/api-gateway -n lawforyou 8080:8080


# Health check
Invoke-RestMethod http://localhost:8080/actuator/health

# Login (returns JWT)
Invoke-RestMethod http://localhost:8080/api/auth/login -Method Post `
  -ContentType "application/json" `
  -Body '{"email":"admin@lawforyou.dev","password":"Admin@12345"}'
```
## Uninstall / Stop

### Uninstall a single service

```powershell


helm uninstall user-service -n lawforyou
```

### Uninstall everything

```powershell
helm uninstall lawforyou-infra user-service case-service document-service `
  communication-service api-gateway config-server eureka-server -n lawforyou

```

# Also delete persistent data (required for a clean reinstall)

```powershell
kubectl delete pvc --all -n lawforyou
```
### Pause minikube (saves state, stops VM)

```powershell
minikube stop

minikube delete
```

### Restart minikube

```powershell
minikube start
.\scripts\install-all.ps1        # GHCR images
# OR
.\scripts\build-local.ps1 -Deploy  # local images
```

