# install-all.ps1 - Bootstrap all LawForYou services on a fresh cluster (Windows).
# Deploys: infra → config-server → eureka-server → business services
#
# Usage:
#   .\scripts\install-all.ps1
#   .\scripts\install-all.ps1 -Namespace lawforyou
param(
    [string]$Namespace = "lawforyou"
)
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$HelmDir   = Join-Path $ScriptDir "..\helm\charts"
$InfraDir  = Join-Path $ScriptDir "..\helm\infra"

function Deploy-Service {
    param(
        [string]  $Name,
        [string]  $ChartPath,
        [string[]]$ValuesFiles = @(),
        [switch]  $Wait,
        [int]     $TimeoutMinutes = 5
    )
    Write-Host ""
    Write-Host "------------------------------------------" -ForegroundColor Cyan
    Write-Host " Installing / upgrading: $Name" -ForegroundColor Cyan
    Write-Host "------------------------------------------" -ForegroundColor Cyan
    $helmArgs = @(
        "upgrade", "--install", $Name, $ChartPath,
        "--namespace", $Namespace,
        "--timeout", "${TimeoutMinutes}m"
    )
    foreach ($vf in $ValuesFiles) {
        if ($vf -and (Test-Path $vf)) { $helmArgs += @("-f", $vf) }
    }
    if ($Wait) { $helmArgs += "--wait" }
    helm @helmArgs
    if ($LASTEXITCODE -ne 0) { Write-Error "Helm install failed for $Name"; exit 1 }
}

# ── 1. Infrastructure ──────────────────────────────────────────────────────────
# values.yaml        — base config (committed, no credentials)
# values-local.yaml  — credential overrides (gitignored; copy from values-local.yaml.example)
$InfraLocalValues = Join-Path $InfraDir "values-local.yaml"
if (-not (Test-Path $InfraLocalValues)) {
    Write-Warning "helm/infra/values-local.yaml not found — copying from example template."
    Copy-Item (Join-Path $InfraDir "values-local.yaml.example") $InfraLocalValues
}
Deploy-Service "lawforyou-infra" $InfraDir @(
    (Join-Path $InfraDir "values.yaml"),
    $InfraLocalValues
) -Wait -TimeoutMinutes 10

# ── 2. Infra-tier services (must be ready before app services) ─────────────────
Deploy-Service "config-server"  (Join-Path $HelmDir "config-server")  -Wait -TimeoutMinutes 10
Deploy-Service "eureka-server"  (Join-Path $HelmDir "eureka-server")  -Wait -TimeoutMinutes 10

# ── 3. Business services (start asynchronously; startupProbe gives 510s window) ─
foreach ($svc in @("user-service","case-service","document-service","communication-service","api-gateway")) {
    Deploy-Service $svc (Join-Path $HelmDir $svc)
}

Write-Host ""
Write-Host "[OK] All services deployed to namespace: $Namespace" -ForegroundColor Green
Write-Host "[i] Business services are starting asynchronously (~90s JVM warm-up)." -ForegroundColor Yellow
Write-Host "[i] Monitor: kubectl get pods -n $Namespace -w" -ForegroundColor Yellow
Write-Host ""
kubectl get pods -n $Namespace