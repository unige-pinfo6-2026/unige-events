// Self-contained end-of-test summary (no remote imports): writes results/summary.json + a compact stdout.
export function handleSummary(data) {
  return {
    'results/summary.json': JSON.stringify(data, null, 2),
    stdout: render(data) + '\n',
  };
}

function round(v) { return typeof v === 'number' ? Math.round(v * 100) / 100 : v; }
function mv(data, name, key) {
  const m = data.metrics[name];
  if (!m || !m.values || m.values[key] === undefined) return 'n/a';
  return round(m.values[key]);
}
function pct(data, name) {
  const m = data.metrics[name];
  if (!m || !m.values || m.values.rate === undefined) return 'n/a';
  return Math.round(m.values.rate * 10000) / 100 + '%';
}

function render(data) {
  const L = [];
  L.push('==================== k6 load test summary ====================');
  L.push('iterations:          ' + mv(data, 'iterations', 'count'));
  L.push('http_reqs:           ' + mv(data, 'http_reqs', 'count') + '  (' + mv(data, 'http_reqs', 'rate') + '/s)');
  L.push('http_req_duration:   avg=' + mv(data, 'http_req_duration', 'avg') + 'ms  p95=' + mv(data, 'http_req_duration', 'p(95)') + 'ms  p99=' + mv(data, 'http_req_duration', 'p(99)') + 'ms  max=' + mv(data, 'http_req_duration', 'max') + 'ms');
  L.push('http_req_failed:     ' + pct(data, 'http_req_failed') + '  (incl. expected 429/404)');
  L.push('checks:              ' + pct(data, 'checks'));
  L.push('unexpected_failures: ' + pct(data, 'unexpected_failures') + '  <-- error SLO (excludes expected 429/404)');
  L.push('rate_limited (429):  ' + mv(data, 'rate_limited', 'count'));
  L.push('notification_lag:    p95=' + mv(data, 'notification_lag', 'p(95)') + 'ms  count=' + mv(data, 'notification_lag', 'count'));
  L.push('==============================================================');
  L.push('Full metric stream in results/summary.json (+ --out json if enabled).');
  return L.join('\n');
}
