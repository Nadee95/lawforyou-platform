# rollback.ps1 - Instant blue-green rollback (Windows).
#
# Usage:
#   .\scripts\rollback.ps1 -Service user-service
param(
    [Parameter(Mandatory)]
    [string]$Service,
    [string]$Namespace = "lawforyou"
)
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$HelmChart = Join-Path $ScriptDir "..\helm\charts\$Service"
$Current  = (helm get values $Service -n $Namespace -o json | ConvertFrom-Json).activeSlot
$Previous = if ($Current -eq "blue") { "green" } else { "blue" }
Write-Host "Rolling back ${Service}: $Current -> $Previous" -ForegroundColor Yellow
helm upgrade $Service $HelmChart `
    --namespace $Namespace `
    --set activeSlot=$Previous `
    --reuse-values
if ($LASTEXITCODE -ne 0) { Write-Error "Rollback failed"; exit 1 }
Write-Host "[OK] Rollback complete. Traffic now on slot: $Previous" -ForegroundColor Green
Write-Host "     (Previous slot '$Current' is still running)" -ForegroundColor Gray