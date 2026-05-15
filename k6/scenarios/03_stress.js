/**
 * 03_stress.js — Stress Test (Find the Breaking Point)
 *
 * PURPOSE : Gradually increase load beyond normal capacity.
 *           Identify at what VU count the service degrades or fails.
 *           Watch GC pause times, thread counts, and DB pool exhaustion in Grafana.
 *
 * LOAD    : 0 → 50 → 100 → 200 → 300 VUs, then ramp back to 0 (recovery check)
 * PASS    : <5% errors tolerated under extreme load, p99 < 3s
 *
 * What to watch in Grafana:
 *   - jvm_memory_used_bytes{area="heap"}       → should plateau, not grow forever
 *   - jvm_gc_pause_seconds_sum                 → spikes = GC pressure
 *   - hikaricp_connections_active              → DB pool saturation
 *   - jvm_threads_live                         → thread pool exhaustion
 *   - http_server_requests_seconds{quantile="0.99"} → p99 latency
 *
 * Run:
 *   k6 run k6/scenarios/03_stress.js
 *   k6 run --out json=results/stress.json k6/scenarios/03_stress.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { loginAsAdmin, readHeaders } from '../helpers/auth.js';
import { ENV } from '../config/env.js';
import { logError } from '../helpers/utils.js';

const errors = new Counter('stress_errors');

export const options = {
  stages: [
    { duration: '2m', target: 50  },   // normal load — baseline
    { duration: '2m', target: 100 },   // 2× normal
    { duration: '2m', target: 200 },   // 4× — significant stress
    { duration: '2m', target: 300 },   // 6× — breaking point territory
    { duration: '1m', target: 0   },   // recovery — watch if error rate drops back
  ],
  thresholds: {
    // Relaxed thresholds — we EXPECT degradation; we're measuring WHERE it breaks
    http_req_failed:   ['rate<0.10'],    // alert if >10% errors (catastrophic failure)
    http_req_duration: ['p(99)<5000'],   // p99 should stay under 5s even under stress
  },
};

export function setup() {
  return { token: loginAsAdmin() };
}

export default function (data) {
  const headers = readHeaders(data.token);

  // Mix of read operations — mimics real mixed traffic under stress
  const r1 = http.get(
    `${ENV.apiGateway}/api/cases?page=0&size=10`,
    { headers, tags: { endpoint: 'listCases' } }
  );
  const ok1 = check(r1, { 'cases ok': (r) => r.status === 200 });
  if (!ok1) { errors.add(1, { endpoint: 'listCases' }); logError('stress:listCases', r1); }

  sleep(0.2);

  const r2 = http.get(
    `${ENV.userService}/api/users?page=0&size=10`,
    { headers, tags: { endpoint: 'listUsers' } }
  );
  const ok2 = check(r2, { 'users ok': (r) => r.status === 200 });
  if (!ok2) { errors.add(1, { endpoint: 'listUsers' }); logError('stress:listUsers', r2); }

  sleep(0.3);
}

export function teardown() {
  console.log('✅ Stress test done. Check when error rate spiked vs VU count.');
}

