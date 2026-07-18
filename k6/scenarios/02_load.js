/**
 * 02_load.js — Load Test (Normal Traffic)
 *
 * PURPOSE : Simulate realistic production-level concurrent users.
 *           Validates response times and error rates under expected load.
 *
 * LOAD    : Ramp 0 → 20 VUs over 1m, hold at 50 VUs for 3m, ramp down 1m
 * PASS    : <1% errors, p95 < 1s overall, p95 login < 800ms, p95 list < 500ms
 *
 * Run:
 *   k6 run k6/scenarios/02_load.js
 *   k6 run --out json=results/load.json k6/scenarios/02_load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { loginAsAdmin, login, readHeaders } from '../helpers/auth.js';
import { ENV } from '../config/env.js';
import { logError } from '../helpers/utils.js';

// ── Custom per-endpoint error counters ────────────────────────────────────────
// These show up in the summary so you know WHICH endpoint is failing.
const caseErrors = new Counter('case_errors');
const userErrors = new Counter('user_errors');

// ── Status code breakdown — tells us the ROOT CAUSE in the summary ────────────
// Run once, look at CUSTOM section in results to see which counter incremented.
const case429 = new Counter('case_status_429');   // rate limited by gateway
const case403 = new Counter('case_status_403');   // forbidden — auth/permission issue
const case500 = new Counter('case_status_500');   // case-service threw exception
const case502 = new Counter('case_status_502');   // bad gateway (case-service crash)
const case503 = new Counter('case_status_503');   // service unavailable (Eureka miss)
const caseOther = new Counter('case_status_other'); // anything else unexpected

function trackCaseError(res) {
  caseErrors.add(1);
  logError('case', res);
  switch (res.status) {
    case 429: case429.add(1);   break;
    case 403: case403.add(1);   break;
    case 500: case500.add(1);   break;
    case 502: case502.add(1);   break;
    case 503: case503.add(1);   break;
    default:  caseOther.add(1, { status: String(res.status) }); break;
  }
}

export const options = {
  stages: [
    { duration: '1m', target: 20 },   // ramp up
    { duration: '3m', target: 50 },   // hold at peak
    { duration: '1m', target: 0  },   // ramp down
  ],
  thresholds: {
    http_req_failed:                       ['rate<0.01'],   // <1% error rate
    http_req_duration:                     ['p(95)<1000'],  // overall p95 < 1s
    'http_req_duration{scenario:login}':   ['p(95)<800'],   // login p95 < 800ms
    'http_req_duration{scenario:listUsers}': ['p(95)<500'], // list p95 < 500ms
    'http_req_duration{scenario:listCases}': ['p(95)<500'],
  },
};

/** Login once before VUs start — avoids hammering auth during ramp */
export function setup() {
  return { token: loginAsAdmin() };
}

export default function (data) {
  // ── 1. Login (simulates real user authentication) ─────────────
  const loginRes = http.post(
    `${ENV.userService}/api/auth/login`,
    JSON.stringify({ usernameOrEmail: ENV.adminEmail, password: ENV.adminPassword }),
    {
      headers: { 'Content-Type': 'application/json', 'X-Tenant-ID': ENV.tenantId },
      tags:    { scenario: 'login' },
    }
  );
  check(loginRes, { 'login → 200': (r) => r.status === 200 });

  // Use fresh token if login succeeded, fall back to shared setup token
  const token   = loginRes.json('data.accessToken') || data.token;
  const headers = readHeaders(token);

  sleep(0.5);

  // ── 2. List users (read-heavy) ────────────────────────────────
  const usersRes = http.get(
    `${ENV.userService}/api/users?page=0&size=10`,
    { headers, tags: { scenario: 'listUsers' } }
  );
  const usersOk = check(usersRes, {
    'list users → 200':       (r) => r.status === 200,
    'list users has content': (r) => Array.isArray(r.json('content')),
  });
  if (!usersOk) userErrors.add(1, { endpoint: 'listUsers' }) && logError('list users', usersRes);

  sleep(0.3);

  // ── 3. List all cases ─────────────────────────────────────────
  const casesRes = http.get(
    `${ENV.apiGateway}/api/cases?page=0&size=10`,
    { headers, tags: { scenario: 'listCases' } }
  );
  const casesOk = check(casesRes, { 'list cases → 200': (r) => r.status === 200 });
  if (!casesOk) trackCaseError(casesRes);

  sleep(0.3);

  // ── 4. Filter cases by status ─────────────────────────────────
  const openCasesRes = http.get(
    `${ENV.apiGateway}/api/cases?status=OPEN&page=0&size=10`,
    { headers, tags: { scenario: 'listCases' } }
  );
  const openOk = check(openCasesRes, { 'filter OPEN cases → 200': (r) => r.status === 200 });
  if (!openOk) trackCaseError(openCasesRes);

  sleep(0.3);

  // ── 5. User search ─────────────────────────────────────────────
  const searchRes = http.get(
    `${ENV.userService}/api/users/search?q=admin`,
    { headers, tags: { scenario: 'listUsers' } }
  );
  const searchOk = check(searchRes, { 'user search → 200': (r) => r.status === 200 });
  if (!searchOk) { userErrors.add(1, { endpoint: 'userSearch' }); logError('user search', searchRes); }

  sleep(1);
}

export function teardown() {
  console.log('✅ Load test complete. Check Grafana dashboards for JVM metrics.');
}

