<#
.SYNOPSIS
    Quick health check for all LawForYou platform services.
.EXAMPLE
    .\health-check.ps1
#>
$checks = @(
    @{ Name = "Config Server";   Url = "http://localhost:8888/actuator/health" }
    @{ Name = "Eureka Server";   Url = "http://localhost:8761/actuator/health" }
    @{ Name = "User Service";    Url = "http://localhost:8081/actuator/health"  }
    @{ Name = "Prometheus";      Url = "http://localhost:9090/-/ready"          }
    @{ Name = "Grafana";         Url = "http://localhost:3000/api/health"       }
    @{ Name = "Jaeger UI";       Url = "http://localhost:16686/"                }
    @{ Name = "Jaeger API";      Url = "http://localhost:16686/api/services"    }
)
$dockerChecks = @(
    @{ Name = "PostgreSQL"; Container = "lawforyou-postgres" }
    @{ Name = "Redis";      Container = "lawforyou-redis"    }
    @{ Name = "Kafka";      Container = "lawforyou-kafka"    }
)
$pass  = 0
$fail  = 0
$width = ($checks + $dockerChecks | ForEach-Object { $_.Name.Length } | Measure-Object -Maximum).Maximum + 2
function Write-Result($name, $ok, $detail) {
    $label = $name.PadRight($width)
    if ($ok) {
        Write-Host "  $label " -NoNewline
        Write-Host "UP   " -ForegroundColor Green -NoNewline
        Write-Host $detail
    } else {
        Write-Host "  $label " -NoNewline
        Write-Host "DOWN " -ForegroundColor Red -NoNewline
        Write-Host $detail
    }
}
Write-Host ""
Write-Host "  LawForYou - Service Health Check" -ForegroundColor Cyan
Write-Host ("  " + ("-" * ($width + 30))) -ForegroundColor DarkGray
Write-Host ""
Write-Host "  HTTP Endpoints" -ForegroundColor DarkGray
foreach ($c in $checks) {
    try {
        $resp = Invoke-WebRequest -Uri $c.Url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        $ok     = $resp.StatusCode -lt 400
        $detail = "HTTP $($resp.StatusCode)"
        if ($c.Url -match "/actuator/health") {
            try { $json = $resp.Content | ConvertFrom-Json; $detail += "  [$($json.status)]" } catch {}
        }
        if ($ok) { $pass++ } else { $fail++ }
        Write-Result $c.Name $ok $detail
    } catch {
        $fail++
        Write-Result $c.Name $false $_.Exception.Message.Split("`n")[0]
    }
}
Write-Host ""
Write-Host "  Docker Containers" -ForegroundColor DarkGray
foreach ($d in $dockerChecks) {
    try {
        $state  = docker inspect --format '{{.State.Status}}' $d.Container 2>$null
        $ok     = ($state -eq "running")
        $health = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' $d.Container 2>$null
        $detail = "state=$state  health=$health"
        if ($ok) { $pass++ } else { $fail++ }
        Write-Result $d.Name $ok $detail
    } catch {
        $fail++
        Write-Result $d.Name $false "docker inspect failed"
    }
}
Write-Host ""
Write-Host ("  " + ("-" * ($width + 30))) -ForegroundColor DarkGray
$total = $pass + $fail
if ($fail -eq 0) {
    Write-Host "  All $total checks passed." -ForegroundColor Green
} else {
    Write-Host "  $pass/$total passed, $fail failed." -ForegroundColor Yellow
}
Write-Host ""