// v2 — Multi-IP READ capacity ramp (Grafana Cloud, multiple load zones). Un-throttled endpoints only,
// anonymous, non-polluting. Finds the read knee PAST the per-IP edge wall that capped test #1.
// Run with: k6 cloud run -e SKIP_AUTH=1 -e NO_THINK=1 scenarios/cloud-capacity.js
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('cloud_capacity');

export { setup, browseReadOnly, teardown } from '../lib/entry.js';
export { handleSummary } from '../lib/summary.js';
