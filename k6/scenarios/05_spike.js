/**
 * 05_spike.js — Spike Test (Sudden Traffic Burst)
 *
 * PURPOSE : Test how the platform handles an instantaneous jump from
 *           near-zero to extreme load, and whether it recovers.
 *           Simulates real-world events: viral content, marketing campaigns,
 *           flash sales, court filing deadlines.
 *
 * LOAD    : 5 VUs → spike to 500 VUs → back to 5 VUs
 * PASS    : Recovery within 30s of spike end; error rate drops back <1%
 *
 * What to observe:
 *   - Does the service throw 503s / connection refused during spike?
 *   - Does it RECOVER (error rate drops) after spike ends? → Good
 *   - Does it NOT recover (error rate stays high)?         → Bad (circuit-breaker issue)
 *   - jvm_threads_live spike → does thread count go back down in recovery?
 *   - hikaricp_connections_pending during spike
 *
 * Run:
 *   k6 run k6/scenarios/05_spike.js
 *   k6 run --out json=results/spike.json k6/scenarios/05_spike.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAsAdmin, readHeaders } from '../helpers/auth.js';
import { ENV } from '../config/env.js';

export const options = {
  stages: [
    { duration: '1m',  target: 5   },   // baseline — normal low traffic
    { duration: '30s', target: 500 },   // SPIKE — instant jump to 500 VUs
    { duration: '1m',  target: 500 },   // hold the spike
    { duration: '30s', target: 5   },   // drop back — recovery phase
    { duration: '2m',  target: 5   },   // watch recovery under normal load
  ],
  thresholds: {
    // Spike thresholds are intentionally lenient during the spike itself.
    // The key metric is recovery — checked manually in Grafana.
    http_req_failed:   ['rate<0.15'],    // allow up to 15% during spike
    http_req_duration: ['p(99)<8000'],   // p99 allowed up to 8s during spike
  },
};

export function setup() {
  console.log('⚡ Spike test starting — watch Grafana for recovery after the 500 VU peak.');
  return { token: loginAsAdmin() };
}

export default function (data) {
  const headers = readHeaders(data.token);

  // Single lightweight read — spike tests intentionally keep the scenario simple
  // so we're measuring infrastructure response, not business logic
  const r = http.get(
    `${ENV.apiGateway}/api/cases?page=0&size=5`,
    { headers, tags: { scenario: 'spike' } }
  );

  check(r, {
    'spike request completes':   (res) => res.status !== 0,    // not a network failure
    'spike request not 5xx':     (res) => res.status < 500,
    'spike request ok':          (res) => res.status === 200,
  });

  sleep(0.1);  // minimal think time — we WANT concurrent pressure
}

export function teardown() {
  console.log('✅ Spike test done. Compare error rate during spike vs recovery phase in Grafana.');
}

