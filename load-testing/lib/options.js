// Build k6 options per profile (spec §7). Two concurrent scenarios:
//   - browse : READ-ONLY headline load (50–200 VUs)
//   - writes : low constant arrival rate, kept UNDER the Kong per-route caps (§9)
const THRESHOLDS = {
  'http_req_duration{scenario:browse}': ['p(95)<1500', 'p(99)<3000'], // WAN + tunnel budget
  'http_req_duration{scenario:reads}': ['p(95)<1500', 'p(99)<5000'],  // capacity-probe (stress)
  unexpected_failures: ['rate<0.05'],   // expected 429/404 are excluded by the wrapper
  checks: ['rate>0.95'],
  notification_lag: ['p(95)<30000'],    // eventual consistency (ms)
  // v2 — PROTECTIVE abort: stop only on ORIGIN trouble (excludes edge 520-527 & expected 429/404).
  // Edge/Kong 429 and bot challenges are the ceiling we map, NOT a reason to abort.
  origin_errors: [{ threshold: 'rate<0.10', abortOnFail: true, delayAbortEval: '30s' }],
};

// v2 — Grafana Cloud fan-out across load zones (specs §10). A plain `k6 run` ignores options.cloud.
// Zone IDs: verify against the current k6 load-zone list. EU-realistic + IP diversity.
const CLOUD = {
  projectID: Number(__ENV.K6_CLOUD_PROJECT_ID || 7680240),
  distribution: {
    paris:     { loadZone: 'amazon:fr:paris',     percent: 30 },
    frankfurt: { loadZone: 'amazon:de:frankfurt', percent: 30 },
    dublin:    { loadZone: 'amazon:ie:dublin',    percent: 20 },
    london:    { loadZone: 'amazon:gb:london',    percent: 20 },
  },
};

function browseRamping(stages) {
  return { executor: 'ramping-vus', exec: 'browse', startVUs: 0, stages: stages, tags: { scenario: 'browse' }, gracefulStop: '15s' };
}
function writesTrickle(duration, rate) {
  return {
    executor: 'constant-arrival-rate', exec: 'writes',
    rate: rate || 8, timeUnit: '1m', duration: duration,
    preAllocatedVUs: 5, maxVUs: 15, tags: { scenario: 'writes' }, gracefulStop: '20s',
  };
}

export function buildOptions(profile) {
  let scenarios;
  switch (profile) {
    case 'smoke':
      scenarios = {
        browse: { executor: 'shared-iterations', exec: 'browse', vus: 1, iterations: 6, maxDuration: '2m', tags: { scenario: 'browse' } },
        writes: { executor: 'shared-iterations', exec: 'writes', vus: 1, iterations: 3, maxDuration: '2m', startTime: '8s', tags: { scenario: 'writes' } },
      };
      break;
    case 'stress':
      scenarios = {
        browse: browseRamping([
          { duration: '1m', target: 50 }, { duration: '2m', target: 100 },
          { duration: '2m', target: 150 }, { duration: '2m', target: 200 },
          { duration: '1m', target: 200 }, { duration: '1m', target: 0 },
        ]),
        writes: writesTrickle('9m', 8),
      };
      break;
    case 'stress_nolist': // prod-safe capacity probe: un-throttled GET endpoints only, no writes
      scenarios = {
        reads: {
          executor: 'ramping-vus', exec: 'browseReadOnly', startVUs: 0, tags: { scenario: 'reads' }, gracefulStop: '15s',
          stages: [
            { duration: '1m', target: 50 }, { duration: '2m', target: 100 },
            { duration: '2m', target: 150 }, { duration: '2m', target: 200 },
            { duration: '2m', target: 200 }, { duration: '1m', target: 0 },
          ],
        },
      };
      break;
    case 'spike':
      scenarios = {
        browse: browseRamping([
          { duration: '20s', target: 200 }, { duration: '40s', target: 200 }, { duration: '20s', target: 0 },
        ]),
        writes: writesTrickle('80s', 8),
      };
      break;
    case 'soak':
      scenarios = {
        browse: { executor: 'constant-vus', exec: 'browse', vus: 50, duration: '1h', tags: { scenario: 'browse' } },
        writes: writesTrickle('1h', 6),
      };
      break;
    case 'cloud_capacity': // v2 — multi-IP READ capacity ramp (un-throttled endpoints). Run: -e SKIP_AUTH=1 -e NO_THINK=1
      scenarios = {
        reads: {
          executor: 'ramping-arrival-rate', exec: 'browseReadOnly', startRate: 20, timeUnit: '1s',
          preAllocatedVUs: 50, maxVUs: Number(__ENV.MAX_VUS || 300), tags: { scenario: 'reads' }, gracefulStop: '15s',
          stages: [
            { duration: '2m', target: 50 }, { duration: '3m', target: 150 },
            { duration: '3m', target: 300 }, { duration: '3m', target: 500 },
            { duration: '1m', target: 0 },
          ],
        },
      };
      break;
    case 'cloud_notify': // v2 — cross-VU notification_lag probe (A follows B, polls B's unread)
      scenarios = {
        notify: {
          executor: 'constant-arrival-rate', exec: 'notifyProbe',
          rate: Number(__ENV.NOTIFY_RATE || 10), timeUnit: '1m', duration: __ENV.NOTIFY_DURATION || '5m',
          preAllocatedVUs: 10, maxVUs: 30, tags: { scenario: 'notify' }, gracefulStop: '30s',
        },
      };
      break;
    case 'load':
    default:
      scenarios = {
        browse: browseRamping([
          { duration: '2m', target: 50 }, { duration: '6m', target: 50 }, { duration: '2m', target: 0 },
        ]),
        writes: writesTrickle('10m', 8),
      };
  }
  return {
    scenarios: scenarios,
    thresholds: THRESHOLDS,
    cloud: Object.assign({ name: 'UNIGE Events prod v2 — ' + profile }, CLOUD), // ignored by local `k6 run`
    discardResponseBodies: false,
    setupTimeout: '180s',
    teardownTimeout: '600s',
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  };
}
