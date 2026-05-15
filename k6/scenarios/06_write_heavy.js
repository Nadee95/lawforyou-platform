/**
 * 06_write_heavy.js — Write-Heavy Scenario (Case Lifecycle under Load)
 *
 * PURPOSE : Test write throughput and data integrity under concurrent load.
 *           Exercises the full case lifecycle:
 *             POST /api/cases   → create
 *             GET  /api/cases/:id → verify
 *             PATCH /api/cases/:id/status → update status
 *             PATCH /api/cases/:id/assign → assign lawyer
 *
 *           This puts pressure on:
 *             - JPA write transactions + connection pool
 *             - Optimistic locking / concurrent modifications
 *             - PostgreSQL write throughput
 *             - Event publishing (if case events trigger notifications)
 *
 * LOAD    : 25 VUs sustained for 3 minutes (write-heavy is harder on DB)
 *
 * Run:
 *   k6 run k6/scenarios/06_write_heavy.js
 *   k6 run --out json=results/write_heavy.json k6/scenarios/06_write_heavy.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAsAdmin, authHeaders, readHeaders } from '../helpers/auth.js';
import { ENV } from '../config/env.js';

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // warm up
    { duration: '3m',  target: 25 },   // sustained write load
    { duration: '30s', target: 0  },   // ramp down
  ],
  thresholds: {
    http_req_failed:                        ['rate<0.02'],   // stricter — writes must succeed
    http_req_duration:                      ['p(95)<2000'],  // writes are slower than reads
    'http_req_duration{op:createCase}':     ['p(95)<1500'],
    'http_req_duration{op:updateStatus}':   ['p(95)<1000'],
    'http_req_duration{op:assignLawyer}':   ['p(95)<1000'],
  },
};

// Fixed IDs seeded by Flyway
const LAWYER_ID = '00000000-0000-0000-0000-000000000002';
const CLIENT_ID = '00000000-0000-0000-0000-000000000099';

const CASE_TYPES   = ['CIVIL', 'CRIMINAL', 'FAMILY', 'CORPORATE'];
const CASE_STATUSES = ['IN_PROGRESS', 'CLOSED'];

/** Pick random element from array */
function randomOf(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function setup() {
  return { token: loginAsAdmin() };
}

export default function (data) {
  const headers     = authHeaders(data.token);
  const readHdrs    = readHeaders(data.token);

  // ── 1. Create a case ─────────────────────────────────────────
  const caseType = randomOf(CASE_TYPES);
  const createRes = http.post(
    `${ENV.apiGateway}/api/cases`,
    JSON.stringify({
      title:       `Load Test Case — VU ${__VU} iter ${__ITER}`,
      description: `Automatically created by k6 write-heavy scenario`,
      caseType,
      clientId:    CLIENT_ID,
    }),
    { headers, tags: { op: 'createCase' } }
  );

  const created = check(createRes, {
    'create case → 201': (r) => r.status === 201,
    'create case has id': (r) => !!r.json('data.id'),
  });

  if (!created) {
    console.error(`Create case failed: ${createRes.status} — ${createRes.body}`);
    sleep(1);
    return;  // skip rest of iteration if create failed
  }

  const caseId = createRes.json('data.id');
  sleep(0.3);

  // ── 2. Verify the created case ────────────────────────────────
  const getRes = http.get(
    `${ENV.apiGateway}/api/cases/${caseId}`,
    { headers: readHdrs, tags: { op: 'getCase' } }
  );
  check(getRes, {
    'get case → 200':       (r) => r.status === 200,
    'get case id matches':  (r) => r.json('data.id') === caseId,
  });

  sleep(0.3);

  // ── 3. Assign lawyer ──────────────────────────────────────────
  const assignRes = http.patch(
    `${ENV.apiGateway}/api/cases/${caseId}/assign`,
    JSON.stringify({ lawyerId: LAWYER_ID }),
    { headers, tags: { op: 'assignLawyer' } }
  );
  check(assignRes, { 'assign lawyer → 200': (r) => r.status === 200 });

  sleep(0.3);

  // ── 4. Update status ──────────────────────────────────────────
  const newStatus = randomOf(CASE_STATUSES);
  const statusRes = http.patch(
    `${ENV.apiGateway}/api/cases/${caseId}/status`,
    JSON.stringify({ status: newStatus }),
    { headers, tags: { op: 'updateStatus' } }
  );
  check(statusRes, { [`status → ${newStatus} 200`]: (r) => r.status === 200 });

  sleep(1);
}

export function teardown() {
  console.log('✅ Write-heavy test done. Check DB write latency and connection pool metrics in Grafana.');
}

