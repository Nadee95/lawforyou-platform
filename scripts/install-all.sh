#!/usr/bin/env bash
# install-all.sh — Bootstrap all LawForYou services on a fresh cluster.
#
# Usage:
#   chmod +x scripts/install-all.sh
#   ./scripts/install-all.sh
#
# Optional: override namespace
#   NAMESPACE=lawforyou ./scripts/install-all.sh

set -euo pipefail

NS="${NAMESPACE:-lawforyou}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HELM_DIR="$SCRIPT_DIR/../helm/charts"
INFRA_DIR="$SCRIPT_DIR/../helm/infra"

deploy() {
  local name=$1
  local chart=$2
  shift 2
  echo ""
  echo "──────────────────────────────────────────"
  echo " Installing / upgrading: $name"
  echo "──────────────────────────────────────────"
  helm upgrade --install "$name" "$chart" \
    --namespace "$NS" \
    --wait --timeout 5m \
    "$@"
}

# ── 1. Infrastructure ──────────────────────────────────────────────────────────
# values.yaml        — base config (committed, no credentials)
# values-local.yaml  — credential overrides (gitignored; copy from values-local.yaml.example)
INFRA_LOCAL="$INFRA_DIR/values-local.yaml"
if [ ! -f "$INFRA_LOCAL" ]; then
  echo "⚠️  helm/infra/values-local.yaml not found — copying from example template."
  cp "$INFRA_DIR/values-local.yaml.example" "$INFRA_LOCAL"
fi
deploy lawforyou-infra "$INFRA_DIR" \
  -f "$INFRA_DIR/values.yaml" \
  -f "$INFRA_LOCAL" \
  --timeout 10m

# ── 2. Infra-tier services (must be ready before app services) ─────────────────
deploy config-server "$HELM_DIR/config-server" --timeout 10m
deploy eureka-server "$HELM_DIR/eureka-server" --timeout 10m

# ── 3. Business services (start asynchronously; startupProbe gives 510s window) ─
for svc in user-service case-service document-service communication-service api-gateway; do
  helm upgrade --install "$svc" "$HELM_DIR/$svc" \
    --namespace "$NS" \
    --timeout 5m
done

echo ""
echo "✅ All services deployed to namespace: $NS"
echo "ℹ️  Business services are starting asynchronously (~90s JVM warm-up)."
echo "ℹ️  Monitor: kubectl get pods -n $NS -w"
echo ""
kubectl get pods -n "$NS"

