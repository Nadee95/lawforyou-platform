import http from 'k6/http';
import { check } from 'k6';
import { ENV } from '../config/env.js';

/**
 * Logs in as admin and returns the accessToken string.
 * Intended to be called inside setup() so login happens once
 * before VUs start, not on every iteration.
 *
 * @returns {string} accessToken
 */
export function loginAsAdmin() {
  return login(ENV.adminEmail, ENV.adminPassword);
}

/**
 * Logs in as the seeded lawyer user and returns the accessToken.
 *
 * @returns {string} accessToken
 */
export function loginAsLawyer() {
  return login(ENV.lawyerEmail, ENV.lawyerPassword);
}

/**
 * Generic login — returns accessToken or throws if login fails.
 *
 * @param {string} usernameOrEmail
 * @param {string} password
 * @returns {string} accessToken
 */
export function login(usernameOrEmail, password) {
  const res = http.post(
    `${ENV.userService}/api/auth/login`,
    JSON.stringify({ usernameOrEmail, password }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Tenant-ID':  ENV.tenantId,
      },
    }
  );

  const ok = check(res, { [`login(${usernameOrEmail}) → 200`]: (r) => r.status === 200 });
  if (!ok) {
    console.error(`Login failed for ${usernameOrEmail}: HTTP ${res.status} — ${res.body}`);
    throw new Error(`Login failed for ${usernameOrEmail}`);
  }

  const token = res.json('data.accessToken');
  if (!token) {
    throw new Error(`No accessToken in login response for ${usernameOrEmail}`);
  }
  return token;
}

/**
 * Returns standard headers for authenticated JSON requests.
 *
 * @param {string} token  Bearer token
 * @returns {object}
 */
export function authHeaders(token) {
  return {
    Authorization:  `Bearer ${token}`,
    'X-Tenant-ID':  ENV.tenantId,
    'Content-Type': 'application/json',
  };
}

/**
 * Returns auth headers without Content-Type (for GET requests).
 *
 * @param {string} token
 * @returns {object}
 */
export function readHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'X-Tenant-ID': ENV.tenantId,
  };
}

