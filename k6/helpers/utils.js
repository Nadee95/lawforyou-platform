/**
 * utils.js — Shared k6 utility helpers
 *
 * Provides error logging and response inspection utilities
 * used across all k6 load/stress scenarios.
 */

/**
 * Logs a failed HTTP response with status code, body, and optional context.
 * Truncates body to 300 chars to keep logs readable.
 *
 * Usage:
 *   const res = http.get(url, opts);
 *   if (!check(res, { 'ok': r => r.status === 200 })) {
 *     logError('list cases', res);
 *   }
 *
 * @param {string}   label  Short description of the request (e.g. 'list cases')
 * @param {Response} res    k6 HTTP response object
 */
export function logError(label, res) {
  const body = res.body ? res.body.substring(0, 300) : '<empty>';
  console.error(`[FAIL] ${label} → HTTP ${res.status} | url: ${res.url} | body: ${body}`);
}

/**
 * Checks a response and logs errors automatically if the check fails.
 * Returns the check result (boolean).
 *
 * Usage:
 *   checkOrLog('list cases → 200', casesRes, r => r.status === 200);
 *
 * @param {string}   label     Description for both the check and the error log
 * @param {Response} res       k6 HTTP response
 * @param {Function} assertion (r) => boolean
 * @returns {boolean}
 */
export function checkOrLog(label, res, assertion) {
  const passed = assertion(res);
  if (!passed) {
    logError(label, res);
  }
  return passed;
}

/**
 * Formats bytes into a human-readable string (KB / MB).
 *
 * @param {number} bytes
 * @returns {string}
 */
export function formatBytes(bytes) {
  if (bytes < 1024)       return `${bytes} B`;
  if (bytes < 1048576)    return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1048576).toFixed(1)} MB`;
}

/**
 * Returns a random integer between min (inclusive) and max (inclusive).
 * Useful for randomising page numbers, user IDs, etc. in load tests.
 *
 * @param {number} min
 * @param {number} max
 * @returns {number}
 */
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Picks a random element from an array.
 *
 * @param {Array} arr
 * @returns {*}
 */
export function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

