// Tagged request wrapper: records per-endpoint latency, 429 counter, unexpected-failure rate, and a check.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';
import { endpointLatency, rateLimited, unexpectedFailures } from './metrics.js';

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
  unexpectedFailures.add(accepted ? 0 : 1, { endpoint: endpoint });
  check(res, { [method + ' ' + endpoint]: () => accepted }, { endpoint: endpoint, service: service });
  return res;
}
