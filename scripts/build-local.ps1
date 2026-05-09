# build-local.ps1 - Option A: build all images directly into minikube (no Docker push / GHCR needed).
#
# Prerequisites:
#   - minikube running  (minikube start --cpus=4 --memory=8192 --driver=docker)
#   - kubectl + Helm 3 installed
#   - Java 21 available for Gradle
#
# What this script does:
#   1. Compiles & packages all Java services with Gradle bootJar
#   2. Points the Docker CLI at minikube internal daemon
#   3. Builds all 7 Docker images directly inside minikube
#   4. (Optionally) deploys all Helm charts with the local-image overrides
#
# Usage:
#   .\scripts\build-local.ps1                    # build images only
#   .\scripts\build-local.ps1 -Deploy            # build images + helm deploy
#   .\scripts\build-local.ps1 -Deploy -Namespace mynamespace
param(
    [switch]$Deploy,
    [string]$Namespace = "lawforyou"
)
$ErrorActionPreference = "Stop"
$Root     = Split-Path -Parent $MyInvocation.MyCommand.Path | Split-Path -Parent
$SvcDir   = Join-Path $Root "services"
$Gradlew  = Join-Path $Root "gradlew.bat"
$HelmDir  = Join-Path $Root "helm\charts"
$Tag      = "0.1.0"
$Registry = "ghcr.io/nadee95/lawforyou"
# ==============================================================================
#  Step 1 - Build Java JARs
# ==============================================================================
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Step 1 - Gradle bootJar for Java services" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
$javaServices = @("eureka-server","config-server","user-service","case-service","document-service","api-gateway")
$gradleTasks  = $javaServices | ForEach-Object { ":${_}:bootJar" }
& $Gradlew @gradleTasks --no-daemon -q
if ($LASTEXITCODE -ne 0) { Write-Error "Gradle build failed"; exit 1 }
Write-Host "[OK] Java JARs built" -ForegroundColor Green
# ==============================================================================
#  Step 2 - Point Docker at minikube
# ==============================================================================
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Step 2 - Switching Docker to minikube daemon" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
Write-Host "[OK] Docker now targets minikube" -ForegroundColor Green
# ==============================================================================
#  Step 3 - Build Docker images
# ==============================================================================
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Step 3 - Building Docker images into minikube" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
$allServices = @(
    "eureka-server",
    "config-server",
    "user-service",
    "case-service",
    "document-service",
    "communication-service",
    "api-gateway"
)
foreach ($svc in $allServices) {
    $imgRef = "${Registry}/${svc}:${Tag}"
    Write-Host ""
    Write-Host "  Building $imgRef ..." -ForegroundColor Yellow
    docker build -t $imgRef (Join-Path $SvcDir $svc)
    if ($LASTEXITCODE -ne 0) { Write-Error "docker build failed for $svc"; exit 1 }
    Write-Host "  [OK] $imgRef" -ForegroundColor Green
}
Write-Host ""
Write-Host "[OK] All images loaded into minikube" -ForegroundColor Green
Write-Host ""
docker images --filter "reference=${Registry}/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
# ==============================================================================
#  Step 4 - (Optional) Helm deploy
# ==============================================================================
if ($Deploy) {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "  Step 4 - Deploying Helm charts (local mode)" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan

    # Helper: Deploy a service chart with its values-local.yaml override.
    # Use -Wait for infra-tier components that must be fully ready before app services start.
    function Deploy-Local {
        param(
            [string]$Name,
            [string]$ChartPath,
            [string]$ValuesFile,
            [switch]$Wait,
            [int]   $TimeoutMinutes = 10
        )
        Write-Host ""
        Write-Host "  Installing $Name ..." -ForegroundColor Yellow
        $helmArgs = @(
            "upgrade", "--install", $Name, $ChartPath,
            "--namespace", $Namespace,
            "-f", $ValuesFile,
            "--timeout", "${TimeoutMinutes}m"
        )
        if ($Wait) { $helmArgs += "--wait" }
        helm @helmArgs
        if ($LASTEXITCODE -ne 0) { Write-Error "Helm install failed for $Name"; exit 1 }
        Write-Host "  [OK] $Name deployed" -ForegroundColor Green
    }

    # ── 1. Infrastructure (postgres, redis, kafka, mongo, minio) ─────────────
    # IMPORTANT: always pass -f values.yaml explicitly so sub-chart probe/resource
    # overrides are treated as user-supplied values (highest priority) and are not
    # silently overridden by Bitnami sub-chart defaults.
    $infraChart  = Join-Path $Root "helm\infra"
    $infraValues = Join-Path $infraChart "values.yaml"
    Write-Host ""
    Write-Host "  Installing lawforyou-infra (infra dependencies) ..." -ForegroundColor Yellow
    helm upgrade --install lawforyou-infra $infraChart `
        --namespace $Namespace `
        -f $infraValues `
        --wait --timeout 10m
    if ($LASTEXITCODE -ne 0) { Write-Error "Helm install failed for lawforyou-infra"; exit 1 }
    Write-Host "  [OK] lawforyou-infra deployed" -ForegroundColor Green

    # ── 2. config-server + eureka-server (must be ready before app services) ──
    Deploy-Local "config-server"     (Join-Path $HelmDir "config-server")     (Join-Path $HelmDir "config-server\values-local.yaml")     -Wait -TimeoutMinutes 10
    Deploy-Local "eureka-server"     (Join-Path $HelmDir "eureka-server")      (Join-Path $HelmDir "eureka-server\values-local.yaml")     -Wait -TimeoutMinutes 10

    # ── 3. Business services (deploy without --wait; they start in ~90s each) ─
    # JVM startup with -XX:TieredStopAtLevel=1 takes ~80s. startupProbe window is
    # 510s (failureThreshold:50 × periodSeconds:10), so no --wait needed here.
    foreach ($svc in @("user-service","case-service","document-service","communication-service","api-gateway")) {
        Deploy-Local $svc (Join-Path $HelmDir $svc) (Join-Path $HelmDir "$svc\values-local.yaml")
    }

    Write-Host ""
    Write-Host "[OK] All services deployed to namespace: $Namespace" -ForegroundColor Green
    Write-Host "[i] App services are starting in the background (~90s JVM warm-up)." -ForegroundColor Yellow
    Write-Host "[i] Monitor with:  kubectl get pods -n $Namespace -w" -ForegroundColor Yellow
    Write-Host ""
    kubectl get pods -n $Namespace
} else {
    Write-Host "[i] Images are ready. To deploy, run:" -ForegroundColor Yellow
    Write-Host "      .\scripts\build-local.ps1 -Deploy" -ForegroundColor White
}