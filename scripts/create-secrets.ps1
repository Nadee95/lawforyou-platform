# create-secrets.ps1 - Create all Kubernetes secrets required by lawforyou-platform.
#
# [!] EDIT the placeholder values below before running!
#     Never commit real credentials to source control.
#
# Usage:
#   .\scripts\create-secrets.ps1                    # dry-run (prints what WOULD be created)
#   .\scripts\create-secrets.ps1 -Apply             # creates secrets in the cluster
#   .\scripts\create-secrets.ps1 -Apply -Namespace mynamespace
#
# Prerequisites:
#   - kubectl configured and pointing at the right cluster/context
#   - The target namespace must already exist (kubectl apply -f k8s/namespaces/lawforyou.yaml)
param(
    [switch]$Apply,
    [string]$Namespace = "lawforyou"
)
$ErrorActionPreference = "Stop"
# ==============================================================================
#  FILL IN YOUR VALUES HERE
# ==============================================================================
# -- Postgres (used by user-service and case-service) --------------------------
# fullnameOverride in helm/infra/values.yaml -> "lawforyou-postgres"
# initdb script creates user_db and case_db; auth.username = lawforyou
$POSTGRES_HOST     = "lawforyou-postgres"        # matches fullnameOverride in infra chart
$POSTGRES_PORT     = "5432"
$POSTGRES_DB_USER  = "user_db"                   # created by initdb.scripts in infra chart
$POSTGRES_DB_CASE  = "case_db"                   # created by initdb.scripts in infra chart
$POSTGRES_USER     = "lawforyou"                 # matches auth.username in infra chart
$POSTGRES_PASSWORD = "lawforyou"                 # matches auth.password in infra chart
# -- Redis ---------------------------------------------------------------------
# fullnameOverride: lawforyou-redis -> standalone master svc = lawforyou-redis-master
$REDIS_HOST        = "lawforyou-redis-master"    # matches fullnameOverride + "-master"
# -- JWT -----------------------------------------------------------------------
$JWT_SECRET        = "CHANGE_ME_JWT_SECRET_MIN_32_CHARS_LONG_XXX"  # <- replace
# -- Keycloak ------------------------------------------------------------------
$KEYCLOAK_JWKS_URI = "http://keycloak:8080/realms/lawforyou/protocol/openid-connect/certs"
# -- MongoDB (used by document-service) ----------------------------------------
$MONGODB_URI       = "mongodb://lawforyou-mongodb:27017/document_db"
# -- MinIO (used by document-service) ------------------------------------------
# MinIO is included in helm/infra (fullnameOverride: lawforyou-minio)
$MINIO_ENDPOINT    = "http://lawforyou-minio:9000"
$MINIO_ACCESS_KEY  = "minioadmin"               # matches auth.rootUser in infra chart
$MINIO_SECRET_KEY  = "minioadmin"               # matches auth.rootPassword in infra chart
# -- SMTP (used by communication-service) --------------------------------------
$SMTP_HOST         = "smtp.gmail.com"
$SMTP_PORT         = "587"
$SMTP_USER         = "nadeekacsuop@gmail.com"
$SMTP_PASS         = "mzvq jwnz wjog xlhb"
$EMAIL_FROM        = "LawForYou nadeekacsuop@gmail.com"
# ==============================================================================
#  Derived values (no need to edit)
# ==============================================================================
$POSTGRES_URL_USER = "jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB_USER}"
$POSTGRES_URL_CASE = "jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB_CASE}"
# ==============================================================================
#  Helper function
# ==============================================================================
function Create-Secret {
    param(
        [string]$Name,
        [hashtable]$Literals
    )
    Write-Host ""
    Write-Host "-- $Name --" -ForegroundColor Cyan
    $kubectlArgs = @(
        "create", "secret", "generic", $Name,
        "--namespace", $Namespace,
        "--save-config",
        "--dry-run=client",
        "-o", "yaml"
    )
    foreach ($kv in $Literals.GetEnumerator()) {
        $kubectlArgs += "--from-literal=$($kv.Key)=$($kv.Value)"
    }
    if ($Apply) {
        Write-Host "  Creating/updating $Name ..." -ForegroundColor DarkGray
        $yaml = & kubectl @kubectlArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Error "kubectl dry-run failed for $Name"
            exit 1
        }
        $yaml | kubectl apply -f -
        if ($LASTEXITCODE -ne 0) {
            Write-Error "kubectl apply failed for $Name"
            exit 1
        }
        Write-Host "  [OK] $Name" -ForegroundColor Green
    } else {
        Write-Host "  DRY-RUN - would create secret '$Name' in namespace '$Namespace' with keys:" -ForegroundColor Yellow
        foreach ($k in $Literals.Keys) {
            Write-Host "    - $k" -ForegroundColor White
        }
    }
}
# ==============================================================================
#  Create secrets
# ==============================================================================
Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
if ($Apply) {
    Write-Host "  Creating Kubernetes secrets  [APPLY MODE]" -ForegroundColor Cyan
} else {
    Write-Host "  Creating Kubernetes secrets  [DRY-RUN - pass -Apply to execute]" -ForegroundColor Yellow
}
Write-Host "==================================================" -ForegroundColor Cyan
# 1. user-service
Create-Secret -Name "lawforyou-user-secret" -Literals @{
    POSTGRES_URL      = $POSTGRES_URL_USER
    POSTGRES_USER     = $POSTGRES_USER
    POSTGRES_PASSWORD = $POSTGRES_PASSWORD
    REDIS_HOST        = $REDIS_HOST
    JWT_SECRET        = $JWT_SECRET
    KEYCLOAK_JWKS_URI = $KEYCLOAK_JWKS_URI
}
# 2. case-service
Create-Secret -Name "lawforyou-case-secret" -Literals @{
    POSTGRES_URL      = $POSTGRES_URL_CASE
    POSTGRES_USER     = $POSTGRES_USER
    POSTGRES_PASSWORD = $POSTGRES_PASSWORD
    JWT_SECRET        = $JWT_SECRET
}
# 3. document-service
Create-Secret -Name "lawforyou-doc-secret" -Literals @{
    MONGODB_URI      = $MONGODB_URI
    MINIO_ENDPOINT   = $MINIO_ENDPOINT
    MINIO_ACCESS_KEY = $MINIO_ACCESS_KEY
    MINIO_SECRET_KEY = $MINIO_SECRET_KEY
    JWT_SECRET       = $JWT_SECRET
}
# 4. api-gateway
Create-Secret -Name "lawforyou-gateway-secret" -Literals @{
    REDIS_HOST        = $REDIS_HOST
    JWT_SECRET        = $JWT_SECRET
    KEYCLOAK_JWKS_URI = $KEYCLOAK_JWKS_URI
}
# 5. communication-service
Create-Secret -Name "lawforyou-comm-secret" -Literals @{
    SMTP_HOST  = $SMTP_HOST
    SMTP_PORT  = $SMTP_PORT
    SMTP_USER  = $SMTP_USER
    SMTP_PASS  = $SMTP_PASS
    EMAIL_FROM = $EMAIL_FROM
}
Write-Host ""
if ($Apply) {
    Write-Host "[OK] All secrets created/updated in namespace: $Namespace" -ForegroundColor Green
    Write-Host ""
    kubectl get secrets -n $Namespace
} else {
    Write-Host "[i] Dry-run complete. Edit the values at the top of this script, then run:" -ForegroundColor Yellow
    Write-Host "      .\scripts\create-secrets.ps1 -Apply" -ForegroundColor White
}