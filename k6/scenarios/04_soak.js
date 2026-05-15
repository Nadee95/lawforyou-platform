/**
 * 04_soak.js — Soak Test (Memory Leak & Connection Pool Detection)
 *
 * PURPOSE : Run steady load for an extended period to expose problems that only
 *           appear over time:
 *             - Heap memory growth (memory leaks)
 *             - HikariCP connection pool leaks
 *             - Thread leaks (threads created but never destroyed)
 *             - GC behaviour under sustained load
 *             - Token/session accumulation in Redis/DB
 *
 * LOAD    : 30 VUs sustained for 2 hours (adjust duration for quick test)
 *
 * Quick run (15 min instead of 2h):
 *   k6 run -e SOAK_DURATION=15m k6/scenarios/04_soak.js
 *
 * Full 2h soak:
 *   k6 run --out json=results/soak.json k6/scenarios/04_soak.js
 *
 * What to watch in Grafana over time:
 *   - jvm_memory_used_bytes         → linear growth = memory leak
 *   - hikaricp_connections_pending  → should be ~0; growth = connection leak
 *   - jvm_threads_live              → should be stable; growth = thread leak
 *   - process_open_fds              → file descriptor leak
 *   - http_req_duration p95/p99     → should stay flat across the 2h window
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAsAdmin, readHeaders } from '../helpers/auth.js';
import { ENV } from '../config/env.js';

const SOAK_DURATION = __ENV.SOAK_DURATION || '2h';

export const options = {
  stages: [
    { duration: '5m',           target: 30 },   // gentle ramp up
    { duration: SOAK_DURATION,  target: 30 },   // sustained — the actual soak
    { duration: '5m',           target: 0  },   // ramp down
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],   // stricter than stress — this is normal load
    http_req_duration: ['p(95)<800'],   // p95 must stay flat throughout
  },
};

export function setup() {
  console.log(`⏱  Soak duration: ${SOAK_DURATION}. Watch Grafana for memory/thread trends.`);
  return { token: loginAsAdmin() };
}

export default function (data) {
  const headers = readHeaders(data.token);

  // Rotate between endpoints to simulate real mixed usage
  const iteration = __ITER % 4;

  switch (iteration) {
    case 0: {
      // List users
      const r = http.get(`${ENV.userService}/api/users?page=0&size=10`, { headers });
      check(r, { 'list users → 200': (res) => res.status === 200 });
      break;
    }
    case 1: {
      // List cases
      const r = http.get(`${ENV.apiGateway}/api/cases?page=0&size=10`, { headers });
      check(r, { 'list cases → 200': (res) => res.status === 200 });
      break;
    }
    case 2: {
      // User search — exercises DB LIKE query
      const r = http.get(`${ENV.userService}/api/users/search?q=admin`, { headers });
      check(r, { 'user search → 200': (res) => res.status === 200 });
      break;
    }
    case 3: {
      // Filter cases by status — exercises JPA filtering + pagination
      const r = http.get(`${ENV.apiGateway}/api/cases?status=OPEN&page=0&size=10`, { headers });
      check(r, { 'filter cases → 200': (res) => res.status === 200 });
      break;
    }
  }

  // Slower iteration pace — soak tests care about duration, not raw throughput
  sleep(2);
}

export function teardown() {
  console.log('✅ Soak test complete. Compare heap at t=0 vs t=end in Grafana memory panel.');
}

