// Tagged request wrapper: records per-endpoint latency, 429 counter, unexpected-failure rate, and a check.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';
import { endpointLatency, rateLimited, unexpectedFailures, edgeBlocks, edgeCache, originErrors } from './metrics.js';

// Read a header case-insensitively (k6 normalizes most, but Cloudflare casing varies).
function h(res, name) {
  return res.headers[name] || res.headers[name.toLowerCase()] || '';
}

// Classify a response as Cloudflare-edge vs application-origin (specs §7). Best-effort:
// the presence of Kong's RateLimit-* headers is the primary 429 discriminator.
function classifyEdge(res) {
  const cfRay = h(res, 'Cf-Ray');
  const isEdge = !!cfRay || /cloudflare/i.test(h(res, 'Server'));
  const kongRL = !!(h(res, 'RateLimit-Limit') || h(res, 'X-RateLimit-Limit') || h(res, 'X-Ratelimit-Limit'));

  const cache = h(res, 'Cf-Cache-Status');
  if (cache) edgeCache.add(1, { status: cache });

  if (res.status >= 520 && res.status <= 527) {
    edgeBlocks.add(1, { kind: 'edge_5xx' });                     // Cloudflare<->origin edge error
  } else if (h(res, 'Cf-Mitigated') || (res.status === 403 && isEdge && /just a moment|attention required|cf-/i.test(String(res.body || '').slice(0, 600)))) {
    edgeBlocks.add(1, { kind: 'challenge' });                    // bot/managed challenge (1010/1020)
  } else if (res.status === 429 && isEdge && !kongRL) {
    edgeBlocks.add(1, { kind: 'edge_429' });                     // Cloudflare per-IP rate limit (~1015)
  }

  // origin_errors = real backend trouble only (protective-abort signal): status 0 (timeout/transport)
  // or a 5xx that is NOT a Cloudflare 520-527 edge error.
  const isOriginError = res.status === 0 || (res.status >= 500 && !(res.status >= 520 && res.status <= 527));
  originErrors.add(isOriginError ? 1 : 0);
}

// opts: { token, body, tags:{endpoint,service,bucket}, expect:[statuses], redirects }
export function req(method, path, opts = {}) {
  const tags = opts.tags || {};
  const endpoint = tags.endpoint || path;
  const service = tags.service || 'unknown';
  const expect = opts.expect || [200];
  const url = path.indexOf('http') === 0 ? path : BASE_URL + path;

  const params = { headers: {}, tags: { endpoint: endpoint, service: service } };
  if (opts.redirects !== undefined) params.redirects = opts.redirects;
  if (opts.token) params.headers['Authorization'] = 'Bearer ' + opts.token;

  let res;
  if (opts.body !== undefined) {
    params.headers['Content-Type'] = 'application/json';
    res = http.request(method, url, JSON.stringify(opts.body), params);
  } else {
    res = http.request(method, url, null, params);
  }

  endpointLatency.add(res.timings.duration, { endpoint: endpoint, service: service, method: method });
  const accepted = expect.indexOf(res.status) !== -1;
  if (res.status === 429) rateLimited.add(1, { bucket: tags.bucket || endpoint });
  classifyEdge(res); // v2: tag Cloudflare edge responses + origin errors (specs §7)
  unexpectedFailures.add(accepted ? 0 : 1, { endpoint: endpoint });
  check(res, { [method + ' ' + endpoint]: () => accepted }, { endpoint: endpoint, service: service });
  return res;
}
