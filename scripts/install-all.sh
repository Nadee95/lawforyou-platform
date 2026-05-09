#!/usr/bin/env bash
# install-all.sh — Bootstrap all LawForYou services on a fresh cluster.
# Run AFTER: namespace, secrets and infra chart are already applied.
#
# Usage:
#   chmod +x scripts/install-all.sh
#   ./scripts/install-all.sh
#
# Optional: override namespace
#   NAMESPACE=lawforyou ./scripts/install-all.sh

set -euo pipefail

NS="${NAMESPACE:-lawforyou}"
HELM_DIR="$(cd "$(dirname "$0")/../helm/charts" && pwd)"

deploy() {
  local name=$1
  echo ""
  echo "──────────────────────────────────────────"
  echo " Installing / upgrading: $name"
  echo "──────────────────────────────────────────"
  helm upgrade --install "$name" "$HELM_DIR/$name" \
    --namespace "$NS" \
    --wait --timeout 5m
}

# ── Infra-tier (rolling update) ──────────────────────────────────────────────
deploy eureka-server
deploy config-server

# ── Business services (blue-green) ───────────────────────────────────────────
deploy user-service
deploy case-service
deploy document-service
deploy communication-service
deploy api-gateway

echo ""
echo "✅ All services deployed to namespace: $NS"
echo ""
kubectl get pods -n "$NS"

