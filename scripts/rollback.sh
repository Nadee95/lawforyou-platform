#!/usr/bin/env bash
# rollback.sh — Instant blue-green rollback: flip active slot back to previous.
#
# Usage:
#   ./scripts/rollback.sh <service-name>
#   ./scripts/rollback.sh user-service

set -euo pipefail

SERVICE="${1:-}"
NS="${NAMESPACE:-lawforyou}"

if [[ -z "$SERVICE" ]]; then
  echo "Usage: $0 <service-name>"
  echo "Example: $0 user-service"
  exit 1
fi

HELM_CHART="$(cd "$(dirname "$0")/../helm/charts/$SERVICE" && pwd)"

CURRENT=$(helm get values "$SERVICE" -n "$NS" -o json | jq -r '.activeSlot')
PREVIOUS=$([[ "$CURRENT" == "blue" ]] && echo "green" || echo "blue")

echo "🔄 Rolling back $SERVICE: $CURRENT → $PREVIOUS"

helm upgrade "$SERVICE" "$HELM_CHART" \
  --namespace "$NS" \
  --set activeSlot="$PREVIOUS" \
  --reuse-values

echo "✅ Rollback complete. Traffic now on slot: $PREVIOUS"
echo "   (Previous slot '$CURRENT' is still running — you can redeploy when ready)"

