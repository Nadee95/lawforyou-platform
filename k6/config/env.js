/**
 * Environment configuration for k6 load tests.
 * Override any value via -e flag:
 *   k6 run -e API_GATEWAY=http://192.168.49.2:30080 scenarios/02_load.js
 *
 * ── Rate Limiter Note ────────────────────────────────────────────────────────
 * The API Gateway rate limiter uses a per-tenant token bucket keyed on X-Tenant-ID.
 * All k6 VUs share the same tenantId → all share the same rate limit bucket.
 * This means 50 VUs competing for one bucket causes artificial 429s at startup.
 *
 * In production this is NOT an issue (many tenants, distributed load).
 * Gateway bucket: replenishRate=50/s, burstCapacity=200 (handles k6 startup burst).
 *
 * To test rate limiting behaviour specifically, reduce burstCapacity in gateway
 * application.yml or reduce k6 VUs below the replenishRate.
 */
export const ENV = {
  apiGateway:      __ENV.API_GATEWAY       || 'http://localhost:8080',
  userService:     __ENV.USER_SERVICE      || 'http://localhost:8081',
  caseService:     __ENV.CASE_SERVICE      || 'http://localhost:8082',
  documentService: __ENV.DOCUMENT_SERVICE  || 'http://localhost:8083',

  tenantId:        __ENV.TENANT_ID         || '00000000-0000-0000-0000-000000000001',

  adminEmail:      __ENV.ADMIN_EMAIL       || 'admin@lawforyou.dev',
  adminPassword:   __ENV.ADMIN_PASSWORD    || 'Admin@12345',

  lawyerEmail:     __ENV.LAWYER_EMAIL      || 'lawyer@lawforyou.dev',
  lawyerPassword:  __ENV.LAWYER_PASSWORD   || 'Lawyer@12345',
};
