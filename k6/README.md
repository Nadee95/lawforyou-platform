# k6 Load Testing — lawforyou-platform

Performance, load, stress, soak, and spike tests for all platform services.

---

## Prerequisites

### Install k6

```powershell
# Windows (winget)
winget install k6 --source winget

# Windows (Chocolatey)
choco install k6

# Verify
k6 version
```

### Services must be running

Start the full stack with Docker Compose before running any test:

```powershell
cd lawforyou-platform
docker compose -f infrastructure/docker-compose/docker-compose.yml up -d
```

---

## Folder Structure

```
k6/
├── config/
│   └── env.js              ← Base URLs and credentials (override with -e flags)
├── helpers/
│   └── auth.js             ← Login helpers; call in setup() once per test
├── scenarios/
│   ├── 01_smoke.js         ← 1 VU / 1 min  — sanity check after deploy
│   ├── 02_load.js          ← 50 VUs / 5 min — normal production traffic
│   ├── 03_stress.js        ← 0→300 VUs      — find the breaking point
│   ├── 04_soak.js          ← 30 VUs / 2h    — memory leak & pool exhaustion
│   ├── 05_spike.js         ← 5→500→5 VUs    — sudden traffic burst recovery
│   └── 06_write_heavy.js   ← 25 VUs / 3 min — full case lifecycle writes
└── README.md
```

---

## Running Tests

All commands assume you are in the **repo root** (`lawforyou-platform/`).

### 1. Smoke — Run this first after every deployment

```powershell
k6 run k6/scenarios/01_smoke.js
```

### 2. Load — Normal traffic simulation

```powershell
k6 run k6/scenarios/02_load.js
```

### 3. Stress — Find the breaking point

```powershell
k6 run k6/scenarios/03_stress.js
```

### 4. Soak — Memory leak detection (quick 15-min version)

```powershell
# Quick run (15 minutes)
k6 run -e SOAK_DURATION=15m k6/scenarios/04_soak.js

# Full 2-hour soak
k6 run k6/scenarios/04_soak.js
```

### 5. Spike — Sudden burst

```powershell
k6 run k6/scenarios/05_spike.js
```

### 6. Write-Heavy — Case lifecycle under concurrent writes

```powershell
k6 run k6/scenarios/06_write_heavy.js
```

---

## Override Environment Variables

Target a different environment (minikube, staging):

```powershell
k6 run `
  -e API_GATEWAY=http://192.168.49.2:30080 `
  -e USER_SERVICE=http://192.168.49.2:30081 `
  -e TENANT_ID=00000000-0000-0000-0000-000000000001 `
  k6/scenarios/02_load.js
```

| Variable          | Default                              | Description                |
|-------------------|--------------------------------------|----------------------------|
| `API_GATEWAY`     | `http://localhost:8080`              | API Gateway URL            |
| `USER_SERVICE`    | `http://localhost:8081`              | User service direct URL    |
| `CASE_SERVICE`    | `http://localhost:8082`              | Case service direct URL    |
| `DOCUMENT_SERVICE`| `http://localhost:8083`              | Document service direct URL|
| `TENANT_ID`       | `00000000-0000-0000-0000-000000000001` | Tenant UUID              |
| `ADMIN_EMAIL`     | `admin@lawforyou.dev`                | Admin credentials          |
| `ADMIN_PASSWORD`  | `Admin@12345`                        | Admin password             |
| `SOAK_DURATION`   | `2h`                                 | Soak test hold duration    |

---

## Saving Output

```powershell
# JSON output (for Grafana / custom analysis)
k6 run --out json=results/load.json k6/scenarios/02_load.js

# CSV output
k6 run --out csv=results/load.csv k6/scenarios/02_load.js
```

---

## Thresholds Summary

| Scenario       | Error Rate  | Latency Target             |
|----------------|-------------|----------------------------|
| Smoke          | < 1%        | p95 < 500ms                |
| Load           | < 1%        | p95 < 1s (login < 800ms)   |
| Stress         | < 10%       | p99 < 5s                   |
| Soak           | < 1%        | p95 < 800ms (held flat)    |
| Spike          | < 15%       | p99 < 8s                   |
| Write-Heavy    | < 2%        | p95 < 2s (create < 1.5s)   |

---

## JVM Metrics to Watch in Grafana During Tests

Open Grafana at `http://localhost:3000` while k6 runs.

| Metric (Prometheus) | Panel | What You're Looking For |
|---|---|---|
| `jvm_memory_used_bytes{area="heap"}` | Memory | Grows then plateaus ✅ / Grows forever ❌ |
| `jvm_gc_pause_seconds_sum` | GC | Short pauses ✅ / Long pause storms ❌ |
| `hikaricp_connections_active` | DB Pool | Stays below pool max ✅ / Hits ceiling ❌ |
| `hikaricp_connections_pending` | DB Pool | Close to 0 ✅ / Growing ❌ |
| `jvm_threads_live` | Threads | Stable count ✅ / Monotonic growth ❌ |
| `http_server_requests_seconds{quantile="0.99"}` | Latency | Flat p99 ✅ / Climbing p99 ❌ |
| `process_open_fds` | System | Stable ✅ / Growing (file descriptor leak) ❌ |

---

## Generating Heap Dumps for Analysis

Add these JVM flags to your service's `application.yml` or Dockerfile `JAVA_OPTS`:

```
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump-<service-name>.hprof
-Xlog:gc*:file=/tmp/gc.log:time,uptime:filecount=5,filesize=20m
```

Then run the soak test and pull the heap dump:

```powershell
# From running container
docker cp <container-id>:/tmp/heapdump-user-service.hprof ./heapdumps/

# Open in Eclipse MAT or IntelliJ Profiler for analysis
```

---

## Scenario Execution Order (Recommended)

```
01_smoke       ← always first after deploy (sanity)
02_load        ← baseline performance benchmark
06_write_heavy ← verify write throughput
05_spike       ← infrastructure resilience
03_stress      ← find breaking point (run last — may crash services)
04_soak        ← run overnight with Grafana open
```

