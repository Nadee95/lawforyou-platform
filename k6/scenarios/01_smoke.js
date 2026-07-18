/**
 * 01_smoke.js — Smoke Test
 *
 * PURPOSE : Quick sanity check — verify the platform responds correctly
 *           before running heavier tests. Run this first after any deployment.
 *
 * LOAD    : 1 VU, 1 minute
 * PASS    : <1% errors, p95 latency < 500ms
 *
 * Run:
 *   k6 run k6/scenarios/01_smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAsAdmin, readHeaders } from '../helpers/auth.js';
import { ENV } from '../config/env.js';

export const options = {
  vus:      1,
  duration: '1m',
  thresholds: {
    http_req_failed:   ['rate<0.01'],   // <1% errors
    http_req_duration: ['p(95)<500'],   // 95th percentile under 500ms
  },
};

/** Runs once before any VU starts — login here to share the token */
export function setup() {
  return { token: loginAsAdmin() };
}

export default function (data) {
  const headers = readHeaders(data.token);

  // ── user-service ──────────────────────────────────────────────

  // Health check
  const health = http.get(`${ENV.userService}/actuator/health`);
  check(health, { 'user-service health UP': (r) => r.status === 200 });

  sleep(0.5);

  // List users
  const users = http.get(`${ENV.userService}/api/users?page=0&size=10`, { headers });
  check(users, {
    'list users → 200':        (r) => r.status === 200,
    'list users has content':  (r) => Array.isArray(r.json('content')),
  });

  sleep(0.5);

  // ── case-service (via API Gateway) ────────────────────────────

  const cases = http.get(`${ENV.apiGateway}/api/cases?page=0&size=10`, { headers });
  check(cases, { 'list cases → 200': (r) => r.status === 200 });

  sleep(0.5);

  // ── api-gateway health ────────────────────────────────────────

  const gw = http.get(`${ENV.apiGateway}/actuator/health`);
  check(gw, { 'api-gateway health UP': (r) => r.status === 200 });

  sleep(1);
}

export function teardown(data) {
  console.log('✅ Smoke test complete.');
}

