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
        [string]$Name,
        [string]$ChartPath,
        [string]$ValuesFile,
        [switch]$Wait,
        [int]   $TimeoutMinutes = 5
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
    if ($ValuesFile -and (Test-Path $ValuesFile)) { $helmArgs += @("-f", $ValuesFile) }
    if ($Wait) { $helmArgs += "--wait" }
    helm @helmArgs
    if ($LASTEXITCODE -ne 0) { Write-Error "Helm install failed for $Name"; exit 1 }
}

# ── 1. Infrastructure ──────────────────────────────────────────────────────────
# Always pass -f values.yaml so sub-chart probe/resource overrides
# are user-supplied (highest priority) and not silently ignored.
Deploy-Service "lawforyou-infra" $InfraDir (Join-Path $InfraDir "values.yaml") -Wait -TimeoutMinutes 10

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