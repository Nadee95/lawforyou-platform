#!/bin/sh
# Resolves ${ENV_VAR} placeholders in the realm template before Keycloak starts.
# The resolved file is written to the import directory and is never persisted outside the container.

set -e

TEMPLATE=/opt/keycloak/realm-template/realm-export-temp.json
OUTPUT=/opt/keycloak/data/import/realm-export.json

echo "[entrypoint] Substituting env vars into realm template..."
envsubst < "$TEMPLATE" > "$OUTPUT"
echo "[entrypoint] Realm file written to $OUTPUT"

exec /opt/keycloak/bin/kc.sh "$@"
